#include "gp2y1014.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "rom/ets_sys.h"

#include <math.h>
#include <string.h>

#define TAG "GP2Y1014"
#define GP2Y_SAMPLE_COUNT 10

static gpio_num_t s_led_pin = GPIO_NUM_NC;
static adc_reader_t s_adc_reader;
static gp2y1014_calibration_t s_calibration;
static bool s_initialized = false;

bool gp2y1014_init(gpio_num_t led_pin,
                   adc_unit_t adc_unit,
                   adc_oneshot_unit_handle_t adc_handle,
                   adc_channel_t channel,
                   const gp2y1014_calibration_t *calibration)
{
    s_initialized = false;
    s_led_pin = led_pin;
    memset(&s_adc_reader, 0, sizeof(s_adc_reader));
    memset(&s_calibration, 0, sizeof(s_calibration));
    if (calibration != NULL) s_calibration = *calibration;
    if (led_pin < 0 || !adc_reader_init(
            &s_adc_reader, adc_unit, adc_handle, channel)) {
        return false;
    }
    gpio_config_t configuration = {
        .pin_bit_mask = 1ULL << led_pin,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    if (gpio_set_level(led_pin, 1) != ESP_OK ||
            gpio_config(&configuration) != ESP_OK ||
            gpio_set_level(led_pin, 1) != ESP_OK) {
        ESP_LOGE(TAG, "GP2Y1014 LED control initialization failed");
        return false;
    }
    s_initialized = true;
    if (!s_calibration.valid) {
        ESP_LOGW(TAG, "GP2Y1014 calibration is missing; PM2.5 values will not be published");
    }
    return true;
}

sensor_status_t gp2y1014_read(gp2y1014_reading_t *reading)
{
    if (reading == NULL) return SENSOR_STATUS_IO_ERROR;
    memset(reading, 0, sizeof(*reading));
    reading->pm25_ug_m3 = NAN;
    if (!s_initialized) {
        reading->status = SENSOR_STATUS_NOT_INITIALIZED;
        return reading->status;
    }
    int64_t total_mv = 0;
    for (size_t i = 0; i < GP2Y_SAMPLE_COUNT; i++) {
        int64_t cycle_started_us = esp_timer_get_time();
        if (gpio_set_level(s_led_pin, 0) != ESP_OK) {
            reading->status = SENSOR_STATUS_IO_ERROR;
            return reading->status;
        }
        ets_delay_us(280);
        int sample_mv = 0;
        sensor_status_t status = adc_reader_read_mv(
                &s_adc_reader, 1, 0, &sample_mv);
        int64_t elapsed_us = esp_timer_get_time() - cycle_started_us;
        if (elapsed_us < 320) ets_delay_us((uint32_t)(320 - elapsed_us));
        if (gpio_set_level(s_led_pin, 1) != ESP_OK) {
            reading->status = SENSOR_STATUS_IO_ERROR;
            return reading->status;
        }
        elapsed_us = esp_timer_get_time() - cycle_started_us;
        if (elapsed_us < 10000) ets_delay_us((uint32_t)(10000 - elapsed_us));
        if (status != SENSOR_STATUS_VALID) {
            reading->status = status;
            return status;
        }
        total_mv += sample_mv;
    }
    reading->adc_millivolts = (int)((total_mv + GP2Y_SAMPLE_COUNT / 2) /
            GP2Y_SAMPLE_COUNT);
    return gp2y1014_convert_adc(
            reading->adc_millivolts, &s_calibration, reading);
}
