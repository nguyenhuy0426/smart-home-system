#include "mq7.h"

#include "esp_log.h"
#include "esp_timer.h"

#include <math.h>
#include <string.h>

#define TAG "MQ7"

static adc_reader_t s_adc_reader;
static mq7_calibration_t s_calibration;
static volatile bool s_initialized = false;
static int64_t s_cycle_started_us = 0;

bool mq7_init(adc_unit_t adc_unit,
              adc_oneshot_unit_handle_t adc_handle,
              adc_channel_t channel,
              const mq7_calibration_t *calibration)
{
    s_initialized = false;
    memset(&s_adc_reader, 0, sizeof(s_adc_reader));
    memset(&s_calibration, 0, sizeof(s_calibration));
    if (calibration != NULL) s_calibration = *calibration;
    if (!adc_reader_init(&s_adc_reader, adc_unit, adc_handle, channel)) {
        return false;
    }
    s_cycle_started_us = esp_timer_get_time();
    s_initialized = true;
    ESP_LOGI(TAG, "initialized on ADC1_CH%d; raw AO acquisition enabled on "
            "every diagnostic cycle", channel);
    if (!s_calibration.valid) {
        ESP_LOGW(TAG, "MQ7 calibration is missing; CO values will not be published");
    }
    return true;
}

sensor_status_t mq7_read(mq7_reading_t *reading)
{
    if (reading == NULL) return SENSOR_STATUS_IO_ERROR;
    memset(reading, 0, sizeof(*reading));
    reading->co_ppm = NAN;
    reading->sensor_resistance_ohm = NAN;
    if (!s_initialized) {
        reading->status = SENSOR_STATUS_NOT_INITIALIZED;
        return reading->status;
    }

    /* The heater is not MCU-controlled on this 3-pin breakout. This phase is
     * diagnostic metadata only and never gates AO acquisition. */
    uint64_t elapsed_ms = (uint64_t)(esp_timer_get_time() - s_cycle_started_us) / 1000;
    reading->phase = mq7_cycle_phase(elapsed_ms);

    sensor_status_t status = adc_reader_read_mv(
            &s_adc_reader,
            16,
            2000,
            &reading->adc_millivolts);

    if (status != SENSOR_STATUS_VALID) {
        reading->status = status;
        return reading->status;
    }

    /* Raw ADC is available on every cycle. Engineering-unit conversion is
     * attempted only by the converter and remains invalid without real,
     * board-specific calibration. */
    sensor_status_t conversion_status = mq7_convert_adc(
            reading->adc_millivolts,
            &s_calibration,
            reading);
    ESP_LOGI(TAG, "AO=%d mV phase=%s calibration=%s conversion=%s",
            reading->adc_millivolts, mq7_heater_phase_name(reading->phase),
            s_calibration.valid ? "present" : "missing",
            sensor_status_name(conversion_status));
    return conversion_status;
}
