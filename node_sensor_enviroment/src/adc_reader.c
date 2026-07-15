#include "adc_reader.h"
#include "adc_sample_math.h"

#include "esp_adc/adc_cali_scheme.h"
#include "esp_log.h"
#include "rom/ets_sys.h"

#include <limits.h>
#include <string.h>

#define TAG "ADC_READER"

bool adc_reader_create_unit(adc_unit_t unit_id,
                            adc_oneshot_unit_handle_t *out_handle)
{
    if (out_handle == NULL) return false;
    *out_handle = NULL;
    adc_oneshot_unit_init_cfg_t configuration = {
        .unit_id = unit_id,
        .ulp_mode = ADC_ULP_MODE_DISABLE,
    };
    esp_err_t error = adc_oneshot_new_unit(&configuration, out_handle);
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "ADC unit initialization failed: %s", esp_err_to_name(error));
        *out_handle = NULL;
        return false;
    }
    return true;
}

bool adc_reader_init(adc_reader_t *reader,
                     adc_unit_t unit_id,
                     adc_oneshot_unit_handle_t unit_handle,
                     adc_channel_t channel)
{
    if (reader == NULL || unit_handle == NULL) return false;
    memset(reader, 0, sizeof(*reader));
    adc_oneshot_chan_cfg_t channel_configuration = {
        .bitwidth = ADC_BITWIDTH_DEFAULT,
        .atten = ADC_ATTEN_DB_12,
    };
    esp_err_t error = adc_oneshot_config_channel(
            unit_handle, channel, &channel_configuration);
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "ADC channel %d configuration failed: %s",
                channel, esp_err_to_name(error));
        return false;
    }

    adc_cali_curve_fitting_config_t calibration_configuration = {
        .unit_id = unit_id,
        .chan = channel,
        .atten = ADC_ATTEN_DB_12,
        .bitwidth = ADC_BITWIDTH_DEFAULT,
    };
    error = adc_cali_create_scheme_curve_fitting(
            &calibration_configuration, &reader->calibration_handle);
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "Calibrated ADC conversion unavailable for channel %d: %s",
                channel, esp_err_to_name(error));
        return false;
    }
    reader->unit_handle = unit_handle;
    reader->channel = channel;
    reader->ready = true;
    return true;
}

sensor_status_t adc_reader_read_mv(const adc_reader_t *reader,
                                   size_t sample_count,
                                   unsigned delay_between_samples_us,
                                   int *out_millivolts)
{
    if (out_millivolts != NULL) *out_millivolts = 0;
    if (reader == NULL || !reader->ready || reader->unit_handle == NULL ||
            reader->calibration_handle == NULL || out_millivolts == NULL ||
            sample_count == 0 || sample_count > 64) {
        return SENSOR_STATUS_NOT_INITIALIZED;
    }

    int raw_samples[64];
    int raw_min = INT_MAX;
    int raw_max = INT_MIN;
    for (size_t i = 0; i < sample_count; i++) {
        if (adc_oneshot_read(reader->unit_handle, reader->channel,
                &raw_samples[i]) != ESP_OK) {
            return SENSOR_STATUS_IO_ERROR;
        }
        if (raw_samples[i] < raw_min) raw_min = raw_samples[i];
        if (raw_samples[i] > raw_max) raw_max = raw_samples[i];
        if (delay_between_samples_us > 0 && i + 1 < sample_count) {
            ets_delay_us(delay_between_samples_us);
        }
    }
    int average_raw = 0;

    sensor_status_t sample_status = adc_samples_average(
            raw_samples,
            sample_count,
            &average_raw);

    if (sample_status != SENSOR_STATUS_VALID) {
        return sample_status;
    }

    int millivolts = 0;

    if (adc_cali_raw_to_voltage(
            reader->calibration_handle,
            average_raw,
            &millivolts) != ESP_OK || millivolts < 0) {
        return SENSOR_STATUS_IO_ERROR;
    }

    ESP_LOGI(TAG, "ADC channel=%d samples=%u raw_first=%d raw_min=%d "
            "raw_max=%d raw_avg=%d calibrated=%d mV status=valid",
            reader->channel, (unsigned)sample_count, raw_samples[0], raw_min,
            raw_max, average_raw, millivolts);
    *out_millivolts = millivolts;
    return SENSOR_STATUS_VALID;
}
