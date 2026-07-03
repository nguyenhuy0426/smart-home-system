#include "mfrc522.h"
#include "mfrc522_protocol.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "rom/ets_sys.h"

#include <string.h>

#define TAG "MFRC522"
#define FIFO_CAPACITY 64
#define TRANSCEIVE_TIMEOUT_US 25000

#define CommandReg 0x01
#define ComIrqReg 0x04
#define ErrorReg 0x06
#define FIFODataReg 0x09
#define FIFOLevelReg 0x0A
#define ControlReg 0x0C
#define BitFramingReg 0x0D
#define ModeReg 0x11
#define TxControlReg 0x14
#define TxASKReg 0x15
#define TModeReg 0x2A
#define TPrescalerReg 0x2B
#define TReloadRegH 0x2C
#define TReloadRegL 0x2D
#define VersionReg 0x37

#define PCD_IDLE 0x00
#define PCD_TRANSCEIVE 0x0C
#define PCD_SOFT_RESET 0x0F
#define PICC_REQA 0x26
#define PICC_CT 0x88
#define PICC_ANTICOLL 0x20
#define PICC_SELECT 0x70
#define PICC_SAK_CASCADE_BIT 0x04

static spi_device_handle_t s_spi_handle = NULL;
static bool s_initialized = false;

static bool write_reg(uint8_t reg, uint8_t value)
{
    if (s_spi_handle == NULL) return false;
    uint8_t tx_data[2] = {(uint8_t)((reg << 1) & 0x7E), value};
    spi_transaction_t transaction = {
        .length = 16,
        .tx_buffer = tx_data,
    };
    return spi_device_transmit(s_spi_handle, &transaction) == ESP_OK;
}

static bool read_reg(uint8_t reg, uint8_t *out_value)
{
    if (s_spi_handle == NULL || out_value == NULL) return false;
    uint8_t tx_data[2] = {(uint8_t)(((reg << 1) & 0x7E) | 0x80), 0};
    uint8_t rx_data[2] = {0};
    spi_transaction_t transaction = {
        .length = 16,
        .tx_buffer = tx_data,
        .rx_buffer = rx_data,
    };
    if (spi_device_transmit(s_spi_handle, &transaction) != ESP_OK) return false;
    *out_value = rx_data[1];
    return true;
}

static mfrc522_status_t transceive(const uint8_t *tx_data,
                                   size_t tx_length,
                                   uint8_t tx_last_bits,
                                   uint8_t *rx_data,
                                   size_t rx_capacity,
                                   size_t *rx_length,
                                   uint8_t *rx_last_bits)
{
    if (tx_data == NULL || tx_length == 0 || tx_length > FIFO_CAPACITY ||
            rx_data == NULL || rx_length == NULL || rx_last_bits == NULL) {
        return MFRC522_PROTOCOL_ERROR;
    }
    *rx_length = 0;
    *rx_last_bits = 0;
    if (!write_reg(CommandReg, PCD_IDLE) || !write_reg(ComIrqReg, 0x7F) ||
            !write_reg(FIFOLevelReg, 0x80) || !write_reg(BitFramingReg, tx_last_bits)) {
        return MFRC522_HARDWARE_ERROR;
    }
    for (size_t i = 0; i < tx_length; i++) {
        if (!write_reg(FIFODataReg, tx_data[i])) return MFRC522_HARDWARE_ERROR;
    }
    if (!write_reg(CommandReg, PCD_TRANSCEIVE) ||
            !write_reg(BitFramingReg, (uint8_t)(tx_last_bits | 0x80))) {
        return MFRC522_HARDWARE_ERROR;
    }

    int64_t deadline = esp_timer_get_time() + TRANSCEIVE_TIMEOUT_US;
    uint8_t irq = 0;
    do {
        if (!read_reg(ComIrqReg, &irq)) return MFRC522_HARDWARE_ERROR;
        if ((irq & 0x01) != 0) {
            write_reg(BitFramingReg, tx_last_bits);
            return MFRC522_TIMEOUT;
        }
    } while ((irq & 0x30) == 0 && esp_timer_get_time() < deadline);
    write_reg(BitFramingReg, tx_last_bits);
    if ((irq & 0x30) == 0) return MFRC522_TIMEOUT;

    uint8_t error = 0;
    uint8_t fifo_level = 0;
    uint8_t control = 0;
    if (!read_reg(ErrorReg, &error) || !read_reg(FIFOLevelReg, &fifo_level) ||
            !read_reg(ControlReg, &control)) {
        return MFRC522_HARDWARE_ERROR;
    }
    if ((error & 0x1B) != 0 || fifo_level == 0 || fifo_level > rx_capacity) {
        return MFRC522_PROTOCOL_ERROR;
    }
    for (uint8_t i = 0; i < fifo_level; i++) {
        if (!read_reg(FIFODataReg, &rx_data[i])) return MFRC522_HARDWARE_ERROR;
    }
    *rx_length = fifo_level;
    *rx_last_bits = control & 0x07;
    return MFRC522_UID_OK;
}

static mfrc522_status_t select_cascade(uint8_t cascade_command,
                                       const uint8_t cascade_uid[5],
                                       uint8_t *out_sak)
{
    uint8_t request[9] = {cascade_command, PICC_SELECT};
    memcpy(request + 2, cascade_uid, 5);
    uint16_t crc = mfrc522_crc_a(request, 7);
    request[7] = (uint8_t)(crc & 0xFF);
    request[8] = (uint8_t)(crc >> 8);

    uint8_t response[3];
    size_t response_length;
    uint8_t response_last_bits;
    mfrc522_status_t status = transceive(request, sizeof(request), 0,
            response, sizeof(response), &response_length, &response_last_bits);
    if (status != MFRC522_UID_OK) return status;
    if (response_length != sizeof(response) || response_last_bits != 0 ||
            !mfrc522_crc_a_valid(response, 1, response + 1)) {
        return MFRC522_PROTOCOL_ERROR;
    }
    *out_sak = response[0];
    return MFRC522_UID_OK;
}

bool mfrc522_init(spi_host_device_t host_id, int mosi_pin, int miso_pin,
                  int sck_pin, int cs_pin)
{
    s_initialized = false;
    spi_bus_config_t bus_config = {
        .miso_io_num = miso_pin,
        .mosi_io_num = mosi_pin,
        .sclk_io_num = sck_pin,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
        .max_transfer_sz = FIFO_CAPACITY,
    };
    spi_device_interface_config_t device_config = {
        .clock_speed_hz = 4000000,
        .mode = 0,
        .spics_io_num = cs_pin,
        .queue_size = 2,
    };

    esp_err_t error = spi_bus_initialize(host_id, &bus_config, SPI_DMA_CH_AUTO);
    if (error != ESP_OK && error != ESP_ERR_INVALID_STATE) {
        ESP_LOGE(TAG, "SPI bus initialization failed: %s", esp_err_to_name(error));
        return false;
    }
    error = spi_bus_add_device(host_id, &device_config, &s_spi_handle);
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "MFRC522 SPI device initialization failed: %s", esp_err_to_name(error));
        return false;
    }

    if (!write_reg(CommandReg, PCD_SOFT_RESET)) return false;
    ets_delay_us(50000);
    uint8_t version = 0;
    if (!read_reg(VersionReg, &version) || version == 0x00 || version == 0xFF) {
        ESP_LOGE(TAG, "MFRC522 is missing or not responding");
        return false;
    }
    bool configured = write_reg(TModeReg, 0x8D) &&
            write_reg(TPrescalerReg, 0x3E) &&
            write_reg(TReloadRegL, 30) && write_reg(TReloadRegH, 0) &&
            write_reg(TxASKReg, 0x40) && write_reg(ModeReg, 0x3D);
    uint8_t antenna = 0;
    if (!configured || !read_reg(TxControlReg, &antenna) ||
            !write_reg(TxControlReg, antenna | 0x03)) {
        ESP_LOGE(TAG, "MFRC522 register configuration failed");
        return false;
    }
    s_initialized = true;
    ESP_LOGI(TAG, "MFRC522 initialized (version 0x%02x)", version);
    return true;
}

mfrc522_status_t mfrc522_read_card_uid(uint8_t *uid, size_t uid_capacity,
                                      uint8_t *uid_len)
{
    if (!s_initialized || s_spi_handle == NULL || uid == NULL || uid_len == NULL ||
            uid_capacity < 10) {
        return MFRC522_HARDWARE_ERROR;
    }
    *uid_len = 0;
    uint8_t response[8];
    size_t response_length;
    uint8_t last_bits;
    uint8_t request = PICC_REQA;
    mfrc522_status_t status = transceive(&request, 1, 7, response, sizeof(response),
            &response_length, &last_bits);
    if (status == MFRC522_TIMEOUT) return MFRC522_NO_CARD;
    if (status != MFRC522_UID_OK) return status;
    if (response_length != 2 || last_bits != 0) return MFRC522_PROTOCOL_ERROR;

    static const uint8_t cascade_commands[] = {0x93, 0x95, 0x97};
    size_t uid_offset = 0;
    for (size_t level = 0; level < sizeof(cascade_commands); level++) {
        uint8_t anticollision[] = {cascade_commands[level], PICC_ANTICOLL};
        status = transceive(anticollision, sizeof(anticollision), 0,
                response, 5, &response_length, &last_bits);
        if (status != MFRC522_UID_OK) return status;
        if (response_length != 5 || last_bits != 0 ||
                !mfrc522_uid_bcc_valid(response)) {
            return MFRC522_PROTOCOL_ERROR;
        }

        bool has_cascade_tag = response[0] == PICC_CT;
        size_t bytes_to_copy = has_cascade_tag ? 3 : 4;
        size_t source_offset = has_cascade_tag ? 1 : 0;
        if (uid_offset + bytes_to_copy > uid_capacity) return MFRC522_PROTOCOL_ERROR;
        memcpy(uid + uid_offset, response + source_offset, bytes_to_copy);
        uid_offset += bytes_to_copy;

        uint8_t sak = 0;
        status = select_cascade(cascade_commands[level], response, &sak);
        if (status != MFRC522_UID_OK) return status;
        bool more_levels = (sak & PICC_SAK_CASCADE_BIT) != 0;
        if (has_cascade_tag != more_levels) return MFRC522_PROTOCOL_ERROR;
        if (!more_levels) {
            if (uid_offset != 4 && uid_offset != 7 && uid_offset != 10) {
                return MFRC522_PROTOCOL_ERROR;
            }
            *uid_len = (uint8_t)uid_offset;
            return MFRC522_UID_OK;
        }
    }
    return MFRC522_PROTOCOL_ERROR;
}
