#include "mq7.h"

#include "driver/ledc.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include <math.h>
#include <string.h>

#define TAG "MQ7"
#define MQ7_PWM_FREQUENCY_HZ 1000
#define MQ7_PWM_MAX_DUTY ((1U << LEDC_TIMER_13_BIT) - 1U)
#define MQ7_HEATER_HIGH_MV 5000.0
#define MQ7_HEATER_LOW_MV 1400.0

static adc_reader_t s_adc_reader;
static mq7_calibration_t s_calibration;
static volatile bool s_initialized = false;
static volatile bool s_heater_fault = false;
static int64_t s_cycle_started_us = 0;
static mq7_heater_phase_t s_applied_phase = (mq7_heater_phase_t)-1;

static bool set_heater_phase(mq7_heater_phase_t phase)
{
    if (phase == s_applied_phase) return true;
    double voltage_ratio = MQ7_HEATER_LOW_MV / MQ7_HEATER_HIGH_MV;
    uint32_t low_duty = (uint32_t)(
            voltage_ratio * voltage_ratio * MQ7_PWM_MAX_DUTY + 0.5);
    uint32_t duty = mq7_cycle_uses_high_voltage(phase)
            ? MQ7_PWM_MAX_DUTY : low_duty;
    if (ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0, duty) != ESP_OK ||
            ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0) != ESP_OK) {
        mq7_heater_off();
        return false;
    }
    s_applied_phase = phase;
    ESP_LOGI(TAG, "MQ7 heater phase: %s", mq7_heater_phase_name(phase));
    return true;
}

static void heater_control_task(void *context)
{
    (void)context;
    while (s_initialized) {
        uint64_t elapsed_ms =
                (uint64_t)(esp_timer_get_time() - s_cycle_started_us) / 1000;
        if (!set_heater_phase(mq7_cycle_phase(elapsed_ms))) {
            s_heater_fault = true;
            break;
        }
        vTaskDelay(pdMS_TO_TICKS(100));
    }
    mq7_heater_off();
    vTaskDelete(NULL);
}

bool mq7_init(adc_unit_t adc_unit,
              adc_oneshot_unit_handle_t adc_handle,
              adc_channel_t channel,
              gpio_num_t heater_control_pin,
              const mq7_calibration_t *calibration)
{
    s_initialized = false;
    s_heater_fault = false;
    s_applied_phase = (mq7_heater_phase_t)-1;
    memset(&s_adc_reader, 0, sizeof(s_adc_reader));
    memset(&s_calibration, 0, sizeof(s_calibration));
    if (calibration != NULL) s_calibration = *calibration;
    if (heater_control_pin < 0 ||
            !adc_reader_init(&s_adc_reader, adc_unit, adc_handle, channel)) {
        return false;
    }
    ledc_timer_config_t timer_configuration = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_13_BIT,
        .timer_num = LEDC_TIMER_0,
        .freq_hz = MQ7_PWM_FREQUENCY_HZ,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    ledc_channel_config_t channel_configuration = {
        .gpio_num = heater_control_pin,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel = LEDC_CHANNEL_0,
        .intr_type = LEDC_INTR_DISABLE,
        .timer_sel = LEDC_TIMER_0,
        .duty = 0,
        .hpoint = 0,
    };
    if (ledc_timer_config(&timer_configuration) != ESP_OK ||
            ledc_channel_config(&channel_configuration) != ESP_OK) {
        mq7_heater_off();
        ESP_LOGE(TAG, "MQ7 heater PWM initialization failed");
        return false;
    }
    s_initialized = true;
    s_cycle_started_us = esp_timer_get_time();
    if (!set_heater_phase(MQ7_PHASE_HIGH_HEAT) ||
            xTaskCreate(heater_control_task, "mq7_heater", 2048,
                    NULL, 5, NULL) != pdPASS) {
        s_initialized = false;
        mq7_heater_off();
        return false;
    }
    if (!s_calibration.valid) {
        ESP_LOGW(TAG, "MQ7 calibration is missing; CO values will not be published");
    }
    return true;
}

void mq7_heater_off(void)
{
    s_initialized = false;
    (void)ledc_stop(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0, 0);
    s_applied_phase = (mq7_heater_phase_t)-1;
}

sensor_status_t mq7_read(mq7_reading_t *reading)
{
    if (reading == NULL) return SENSOR_STATUS_IO_ERROR;
    memset(reading, 0, sizeof(*reading));
    reading->co_ppm = NAN;
    reading->sensor_resistance_ohm = NAN;
    if (s_heater_fault) {
        reading->status = SENSOR_STATUS_IO_ERROR;
        return reading->status;
    }
    if (!s_initialized) {
        reading->status = SENSOR_STATUS_NOT_INITIALIZED;
        return reading->status;
    }

    uint64_t elapsed_ms = (uint64_t)(esp_timer_get_time() - s_cycle_started_us) / 1000;
    reading->phase = mq7_cycle_phase(elapsed_ms);
    if (reading->phase != MQ7_PHASE_SAMPLE) {
        reading->status = SENSOR_STATUS_HEATER_WARMUP;
        return reading->status;
    }
    sensor_status_t status = adc_reader_read_mv(
            &s_adc_reader, 16, 2000, &reading->adc_millivolts);
    if (status != SENSOR_STATUS_VALID) {
        reading->status = status;
        return status;
    }
    return mq7_convert_adc(reading->adc_millivolts, &s_calibration, reading);
}
