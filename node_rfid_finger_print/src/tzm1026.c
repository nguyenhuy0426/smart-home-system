#include "tzm1026.h"
#include "tzm1026_protocol.h"

#include "driver/uart.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include <string.h>

#define TAG "TZM1026"
#define UART_RX_BUFFER_SIZE 256
#define MAX_PACKET_SIZE 64
#define UART_QUIET_MS 20

static int s_uart_num = UART_NUM_1;
static bool s_initialized = false;

static const uint8_t CMD_GET_IMAGE[] = {
    0xEF, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0x00, 0x03, 0x01, 0x00, 0x05
};
static const uint8_t CMD_GEN_CHAR[] = {
    0xEF, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0x00, 0x04, 0x02, 0x01, 0x00, 0x08
};
static const uint8_t CMD_SEARCH[] = {
    0xEF, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0x00, 0x08,
    0x04, 0x01, 0x00, 0x00, 0x00, 0x64, 0x00, 0x72
};

static int read_byte_until(uint8_t *out, int64_t deadline_us)
{
    while (esp_timer_get_time() < deadline_us) {
        int64_t remaining_us = deadline_us - esp_timer_get_time();
        if (remaining_us <= 0) return 0;
        TickType_t ticks = pdMS_TO_TICKS((remaining_us + 999) / 1000);
        if (ticks == 0) ticks = 1;
        int count = uart_read_bytes(s_uart_num, out, 1, ticks);
        if (count == 1) return 1;
        if (count < 0) return -1;
    }
    return 0;
}

static int receive_packet(uint8_t *frame, size_t capacity, int timeout_ms)
{
    int64_t deadline_us = esp_timer_get_time() + (int64_t)timeout_ms * 1000;
    size_t offset = 0;

    while (offset < 2) {
        uint8_t byte;
        int status = read_byte_until(&byte, deadline_us);
        if (status <= 0) return status;
        if (offset == 0 && byte == 0xEF) {
            frame[offset++] = byte;
        } else if (offset == 1 && byte == 0x01) {
            frame[offset++] = byte;
        } else {
            offset = byte == 0xEF ? 1 : 0;
            if (offset == 1) frame[0] = byte;
        }
    }

    while (offset < 9) {
        int status = read_byte_until(&frame[offset], deadline_us);
        if (status <= 0) return status;
        offset++;
    }
    uint16_t packet_length = ((uint16_t)frame[7] << 8) | frame[8];
    size_t total_length = (size_t)packet_length + 9;
    if (packet_length < 3 || total_length > capacity) return -1;
    while (offset < total_length) {
        int status = read_byte_until(&frame[offset], deadline_us);
        if (status <= 0) return status;
        offset++;
    }
    return (int)offset;
}

static tzm1026_scan_status_t execute_command(const uint8_t *command,
                                             size_t command_length,
                                             int timeout_ms,
                                             tzm1026_ack_t *ack)
{
    uint8_t frame[MAX_PACKET_SIZE];
    uart_flush_input(s_uart_num);
    vTaskDelay(pdMS_TO_TICKS(UART_QUIET_MS));
    uart_flush_input(s_uart_num);

    uint64_t started_us = (uint64_t)esp_timer_get_time();
    if (uart_write_bytes(s_uart_num, (const char *)command, command_length) !=
            (int)command_length ||
            uart_wait_tx_done(s_uart_num, pdMS_TO_TICKS(timeout_ms)) != ESP_OK) {
        uart_flush_input(s_uart_num);
        return TZM1026_SCAN_HARDWARE_ERROR;
    }
    int received = receive_packet(frame, sizeof(frame), timeout_ms);
    uint64_t received_at_us = (uint64_t)esp_timer_get_time();
    if (received == 0) return TZM1026_SCAN_TIMEOUT;
    if (received < 0) {
        uart_flush_input(s_uart_num);
        return TZM1026_SCAN_MALFORMED;
    }
    tzm1026_frame_status_t parsed = tzm1026_parse_ack(
            frame, (size_t)received, started_us, received_at_us,
            (uint64_t)timeout_ms * 1000, ack);
    if (parsed == TZM1026_FRAME_STALE) return TZM1026_SCAN_TIMEOUT;
    if (parsed != TZM1026_FRAME_OK) {
        uart_flush_input(s_uart_num);
        return TZM1026_SCAN_MALFORMED;
    }
    return TZM1026_SCAN_MATCH;
}

bool tzm1026_init(int uart_num, int tx_pin, int rx_pin, int baud_rate)
{
    s_uart_num = uart_num;
    s_initialized = false;
    uart_config_t uart_config = {
        .baud_rate = baud_rate,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };

    esp_err_t error = uart_driver_install(
            s_uart_num, UART_RX_BUFFER_SIZE, 0, 0, NULL, 0);
    if (error != ESP_OK && error != ESP_ERR_INVALID_STATE) {
        ESP_LOGE(TAG, "UART driver install failed: %s", esp_err_to_name(error));
        return false;
    }
    error = uart_param_config(s_uart_num, &uart_config);
    if (error == ESP_OK) {
        error = uart_set_pin(s_uart_num, tx_pin, rx_pin,
                UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
    }
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "UART fingerprint configuration failed: %s", esp_err_to_name(error));
        return false;
    }
    uart_flush_input(s_uart_num);
    s_initialized = true;
    ESP_LOGI(TAG, "TZM1026 UART initialized; access remains locked until validated match");
    return true;
}

tzm1026_scan_status_t tzm1026_scan_finger(uint16_t *page_id, uint16_t *match_score)
{
    if (!s_initialized || page_id == NULL || match_score == NULL) {
        return TZM1026_SCAN_HARDWARE_ERROR;
    }
    *page_id = 0;
    *match_score = 0;

    tzm1026_ack_t ack;
    tzm1026_scan_status_t status = execute_command(
            CMD_GET_IMAGE, sizeof(CMD_GET_IMAGE), 250, &ack);
    if (status != TZM1026_SCAN_MATCH) return status;
    if (ack.confirmation_code == 0x02) {
        return ack.data_length == 0 ? TZM1026_SCAN_NO_FINGER : TZM1026_SCAN_MALFORMED;
    }
    if (ack.confirmation_code != 0x00) return TZM1026_SCAN_HARDWARE_ERROR;
    if (ack.data_length != 0) return TZM1026_SCAN_MALFORMED;

    status = execute_command(CMD_GEN_CHAR, sizeof(CMD_GEN_CHAR), 250, &ack);
    if (status != TZM1026_SCAN_MATCH) return status;
    if (ack.confirmation_code != 0x00) return TZM1026_SCAN_HARDWARE_ERROR;
    if (ack.data_length != 0) return TZM1026_SCAN_MALFORMED;

    status = execute_command(CMD_SEARCH, sizeof(CMD_SEARCH), 350, &ack);
    if (status != TZM1026_SCAN_MATCH) return status;
    if (ack.confirmation_code == 0x09) {
        return ack.data_length == 0 ? TZM1026_SCAN_NO_MATCH : TZM1026_SCAN_MALFORMED;
    }
    if (ack.confirmation_code != 0x00 || ack.data_length != 4) {
        return TZM1026_SCAN_MALFORMED;
    }
    *page_id = ((uint16_t)ack.data[0] << 8) | ack.data[1];
    *match_score = ((uint16_t)ack.data[2] << 8) | ack.data[3];
    return TZM1026_SCAN_MATCH;
}
