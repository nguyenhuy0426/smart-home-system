#include "bme680_i2c.h"
#include "bme680_status.h"

#include "bme68x.h"

#include "driver/i2c_master.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "rom/ets_sys.h"

#include <math.h>
#include <string.h>

#define TAG "BME680_I2C"
#define I2C_TIMEOUT_MS 100
#define BME680_HEATER_TEMPERATURE_C 300
#define BME680_HEATER_DURATION_MS 100

typedef struct {
    i2c_master_dev_handle_t device;
} bme680_interface_t;

static i2c_master_bus_handle_t s_bus_handle = NULL;
static bme680_interface_t s_interface;
static struct bme68x_dev s_device;
static struct bme68x_conf s_configuration;
static struct bme68x_heatr_conf s_heater_configuration;
static bool s_initialized = false;

static int8_t interface_read(uint8_t register_address,
                             uint8_t *register_data,
                             uint32_t length,
                             void *interface_pointer)
{
    bme680_interface_t *interface = interface_pointer;
    if (interface == NULL || interface->device == NULL || register_data == NULL ||
            length == 0 || length > 128) {
        return -1;
    }
    return i2c_master_transmit_receive(interface->device,
            &register_address, 1, register_data, length, I2C_TIMEOUT_MS) == ESP_OK
            ? BME68X_INTF_RET_SUCCESS : -1;
}

static int8_t interface_write(uint8_t register_address,
                              const uint8_t *register_data,
                              uint32_t length,
                              void *interface_pointer)
{
    bme680_interface_t *interface = interface_pointer;
    uint8_t frame[129];
    if (interface == NULL || interface->device == NULL || register_data == NULL ||
            length == 0 || length > sizeof(frame) - 1) {
        return -1;
    }
    frame[0] = register_address;
    memcpy(frame + 1, register_data, length);
    return i2c_master_transmit(interface->device,
            frame, length + 1, I2C_TIMEOUT_MS) == ESP_OK
            ? BME68X_INTF_RET_SUCCESS : -1;
}

static void interface_delay_us(uint32_t period_us, void *interface_pointer)
{
    (void)interface_pointer;
    if (period_us >= 2000) {
        TickType_t ticks = pdMS_TO_TICKS((period_us + 999) / 1000);
        vTaskDelay(ticks == 0 ? 1 : ticks);
    } else {
        ets_delay_us(period_us);
    }
}

static bool attach_sensor(uint8_t address)
{
    i2c_device_config_t device_configuration = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = address,
        .scl_speed_hz = 100000,
    };
    if (i2c_master_bus_add_device(s_bus_handle,
            &device_configuration, &s_interface.device) != ESP_OK) {
        s_interface.device = NULL;
        return false;
    }

    memset(&s_device, 0, sizeof(s_device));
    s_device.intf = BME68X_I2C_INTF;
    s_device.intf_ptr = &s_interface;
    s_device.read = interface_read;
    s_device.write = interface_write;
    s_device.delay_us = interface_delay_us;
    s_device.amb_temp = 25;
    int8_t result = bme68x_init(&s_device);
    if (result != BME68X_OK || s_device.chip_id != BME68X_CHIP_ID) {
        ESP_LOGW(TAG, "bme68x_init at 0x%02x failed: result=%d chip_id=0x%02x "
                "(expected 0x%02x)", address, (int)result,
                (unsigned)s_device.chip_id, (unsigned)BME68X_CHIP_ID);
        (void)i2c_master_bus_rm_device(s_interface.device);
        s_interface.device = NULL;
        return false;
    }
    ESP_LOGI(TAG, "BME680 detected address=0x%02x chip_id=0x%02x init=%d",
            address, (unsigned)s_device.chip_id, (int)result);
    return true;
}

/* ACK-probe both documented BME680 addresses and log the exact result, so
 * the monitor output distinguishes "no device on the bus" from "device
 * present but init failed". Diagnostic only; attach_sensor() decides. */
static void probe_addresses(void)
{
    const uint8_t addresses[] = { BME68X_I2C_ADDR_LOW, BME68X_I2C_ADDR_HIGH };
    for (size_t i = 0; i < sizeof(addresses); i++) {
        esp_err_t probe = i2c_master_probe(s_bus_handle, addresses[i],
                I2C_TIMEOUT_MS);
        ESP_LOGI(TAG, "I2C probe 0x%02x: %s", addresses[i],
                probe == ESP_OK ? "ACK" : esp_err_to_name(probe));
    }
}

bool bme680_i2c_init(gpio_num_t sda_pin, gpio_num_t scl_pin)
{
    s_initialized = false;
    memset(&s_interface, 0, sizeof(s_interface));
    if (sda_pin < 0 || scl_pin < 0) return false;
    i2c_master_bus_config_t bus_configuration = {
        .i2c_port = -1,
        .sda_io_num = sda_pin,
        .scl_io_num = scl_pin,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    esp_err_t error = i2c_new_master_bus(&bus_configuration, &s_bus_handle);
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "BME680 I2C bus initialization failed: %s",
                esp_err_to_name(error));
        return false;
    }
    probe_addresses();
    if (!attach_sensor(BME68X_I2C_ADDR_LOW) &&
            !attach_sensor(BME68X_I2C_ADDR_HIGH)) {
        ESP_LOGE(TAG, "BME680 is missing at both 0x%02x and 0x%02x on "
                "SDA=GPIO%d SCL=GPIO%d, or its calibration block could not "
                "be read", BME68X_I2C_ADDR_LOW, BME68X_I2C_ADDR_HIGH,
                sda_pin, scl_pin);
        return false;
    }

    memset(&s_configuration, 0, sizeof(s_configuration));
    s_configuration.filter = BME68X_FILTER_SIZE_3;
    s_configuration.odr = BME68X_ODR_NONE;
    s_configuration.os_hum = BME68X_OS_2X;
    s_configuration.os_pres = BME68X_OS_4X;
    s_configuration.os_temp = BME68X_OS_8X;
    if (bme68x_set_conf(&s_configuration, &s_device) != BME68X_OK) {
        ESP_LOGE(TAG, "BME680 compensation/oversampling configuration failed");
        return false;
    }

    memset(&s_heater_configuration, 0, sizeof(s_heater_configuration));
    s_heater_configuration.enable = BME68X_ENABLE;
    s_heater_configuration.heatr_temp = BME680_HEATER_TEMPERATURE_C;
    s_heater_configuration.heatr_dur = BME680_HEATER_DURATION_MS;
    if (bme68x_set_heatr_conf(BME68X_FORCED_MODE,
            &s_heater_configuration, &s_device) != BME68X_OK) {
        ESP_LOGE(TAG, "BME680 gas heater configuration failed");
        return false;
    }
    s_initialized = true;
    return true;
}

sensor_status_t bme680_i2c_read(bme680_data_t *data)
{
    if (data == NULL) return SENSOR_STATUS_IO_ERROR;
    memset(data, 0, sizeof(*data));
    data->temperature_degc = NAN;
    data->humidity_percent = NAN;
    data->pressure_hpa = NAN;
    data->gas_resistance_ohm = NAN;
    data->status = SENSOR_STATUS_NOT_INITIALIZED;
    data->gas_status = SENSOR_STATUS_NOT_INITIALIZED;
    if (!s_initialized || s_interface.device == NULL) return data->status;

    if (bme68x_set_op_mode(BME68X_FORCED_MODE, &s_device) != BME68X_OK) {
        data->status = SENSOR_STATUS_IO_ERROR;
        data->gas_status = SENSOR_STATUS_IO_ERROR;
        return data->status;
    }
    uint32_t measurement_duration_us = bme68x_get_meas_dur(
            BME68X_FORCED_MODE, &s_configuration, &s_device) +
            (uint32_t)s_heater_configuration.heatr_dur * 1000U;
    interface_delay_us(measurement_duration_us, &s_interface);

    struct bme68x_data raw_data;
    memset(&raw_data, 0, sizeof(raw_data));
    uint8_t field_count = 0;
    int8_t result = bme68x_get_data(
            BME68X_FORCED_MODE, &raw_data, &field_count, &s_device);
    data->status = bme680_validate_frame_status(
            result, field_count, raw_data.status, &data->gas_status);
    if (data->status != SENSOR_STATUS_VALID) {
        return data->status;
    }

#ifdef BME68X_USE_FPU
    data->temperature_degc = raw_data.temperature;
    data->humidity_percent = raw_data.humidity;
    data->pressure_hpa = raw_data.pressure / 100.0;
    data->gas_resistance_ohm = raw_data.gas_resistance;
#else
    data->temperature_degc = raw_data.temperature / 100.0;
    data->humidity_percent = raw_data.humidity / 1000.0;
    data->pressure_hpa = raw_data.pressure / 100.0;
    data->gas_resistance_ohm = raw_data.gas_resistance;
#endif
    if (!isfinite(data->temperature_degc) ||
            !isfinite(data->humidity_percent) ||
            !isfinite(data->pressure_hpa) ||
            data->temperature_degc < -40.0 || data->temperature_degc > 85.0 ||
            data->humidity_percent < 0.0 || data->humidity_percent > 100.0 ||
            data->pressure_hpa < 300.0 || data->pressure_hpa > 1100.0) {
        data->status = SENSOR_STATUS_OUT_OF_RANGE;
        data->gas_status = SENSOR_STATUS_OUT_OF_RANGE;
        return data->status;
    }
    data->status = SENSOR_STATUS_VALID;
    if (data->gas_status != SENSOR_STATUS_VALID) {
        data->gas_resistance_ohm = NAN;
    } else if (!isfinite(data->gas_resistance_ohm) ||
            data->gas_resistance_ohm <= 0.0) {
        data->gas_status = SENSOR_STATUS_OUT_OF_RANGE;
        data->gas_resistance_ohm = NAN;
    } else {
        data->gas_status = SENSOR_STATUS_VALID;
    }
    return data->status;
}
