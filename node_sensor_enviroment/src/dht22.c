#include "dht22.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "rom/ets_sys.h"

#include <math.h>
#include <stddef.h>

#define TAG "DHT22"
#define DHT22_MIN_READ_INTERVAL_US UINT64_C(2000000)

static gpio_num_t s_dht_pin = GPIO_NUM_NC;
static bool s_initialized = false;
static uint64_t s_last_attempt_us = 0;

static int wait_for_level(int level, uint32_t timeout_us)
{
    int64_t started_us = esp_timer_get_time();
    while (gpio_get_level(s_dht_pin) != level) {
        if ((uint64_t)(esp_timer_get_time() - started_us) >= timeout_us) return -1;
        ets_delay_us(1);
    }
    return (int)(esp_timer_get_time() - started_us);
}

bool dht22_init(gpio_num_t pin)
{
    s_initialized = false;
    s_dht_pin = pin;
    s_last_attempt_us = 0;
    if (pin < 0) return false;
    gpio_config_t configuration = {
        .pin_bit_mask = 1ULL << pin,
        .mode = GPIO_MODE_INPUT_OUTPUT_OD,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    if (gpio_config(&configuration) != ESP_OK ||
            gpio_set_level(pin, 1) != ESP_OK) {
        ESP_LOGE(TAG, "DHT22 GPIO initialization failed");
        return false;
    }
    s_initialized = true;
    return true;
}

static sensor_status_t read_once(double *temperature, double *humidity)
{
    uint16_t high_pulse_us[40] = {0};

    if (gpio_set_direction(s_dht_pin, GPIO_MODE_OUTPUT_OD) != ESP_OK ||
            gpio_set_level(s_dht_pin, 0) != ESP_OK) {
        return SENSOR_STATUS_IO_ERROR;
    }
    ets_delay_us(2000);
    if (gpio_set_level(s_dht_pin, 1) != ESP_OK) return SENSOR_STATUS_IO_ERROR;
    ets_delay_us(30);
    if (gpio_set_direction(s_dht_pin, GPIO_MODE_INPUT) != ESP_OK) {
        return SENSOR_STATUS_IO_ERROR;
    }

    if (wait_for_level(0, 100) < 0 || wait_for_level(1, 100) < 0 ||
            wait_for_level(0, 100) < 0) {
        ESP_LOGW(TAG, "DHT22 response timeout");
        return SENSOR_STATUS_TIMEOUT;
    }
    for (size_t bit = 0; bit < 40; bit++) {
        if (wait_for_level(1, 70) < 0) return SENSOR_STATUS_TIMEOUT;
        int high_time_us = wait_for_level(0, 100);
        if (high_time_us < 0) return SENSOR_STATUS_TIMEOUT;
        high_pulse_us[bit] = (uint16_t)high_time_us;
    }
    return dht22_decode_pulses(high_pulse_us, 40, temperature, humidity);
}

sensor_status_t dht22_read(double *temperature, double *humidity)
{
    if (temperature != NULL) *temperature = NAN;
    if (humidity != NULL) *humidity = NAN;
    if (!s_initialized || temperature == NULL || humidity == NULL) {
        return SENSOR_STATUS_NOT_INITIALIZED;
    }
    uint64_t now_us = (uint64_t)esp_timer_get_time();
    if (s_last_attempt_us != 0 && now_us - s_last_attempt_us < DHT22_MIN_READ_INTERVAL_US) {
        return SENSOR_STATUS_RATE_LIMITED;
    }

    sensor_status_t status = SENSOR_STATUS_TIMEOUT;
    for (unsigned attempt = 0; attempt < 2; attempt++) {
        s_last_attempt_us = (uint64_t)esp_timer_get_time();
        status = read_once(temperature, humidity);
        if (status == SENSOR_STATUS_VALID ||
                (status != SENSOR_STATUS_TIMEOUT &&
                 status != SENSOR_STATUS_CHECKSUM_ERROR)) {
            break;
        }
        if (attempt == 0) {
            vTaskDelay(pdMS_TO_TICKS(2100));
        }
    }
    if (status != SENSOR_STATUS_VALID) {
        ESP_LOGW(TAG, "DHT22 frame rejected: %s", sensor_status_name(status));
    }
    return status;
}
