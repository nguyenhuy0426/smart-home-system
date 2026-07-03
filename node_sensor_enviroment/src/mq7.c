#include "mq7.h"
#include "esp_log.h"

#define TAG "MQ7"

static adc_oneshot_unit_handle_t s_adc_handle = NULL;
static adc_channel_t s_channel;
static bool s_initialized = false;

void mq7_init(adc_unit_t adc_unit, adc_channel_t channel)
{
    s_channel = channel;

    adc_oneshot_unit_init_cfg_t init_config = {
        .unit_id = adc_unit,
        .ulp_mode = ADC_ULP_MODE_DISABLE,
    };

    esp_err_t err = adc_oneshot_new_unit(&init_config, &s_adc_handle);
    if (err == ESP_OK) {
        adc_oneshot_chan_cfg_t config = {
            .bitwidth = ADC_BITWIDTH_DEFAULT,
            .atten = ADC_ATTEN_DB_12,
        };
        err = adc_oneshot_config_channel(s_adc_handle, s_channel, &config);
        if (err == ESP_OK) {
            s_initialized = true;
            ESP_LOGI(TAG, "MQ7 ADC Channel %d configured successfully", channel);
        } else {
            ESP_LOGE(TAG, "Failed to configure ADC channel: %s", esp_err_to_name(err));
        }
    } else {
        ESP_LOGE(TAG, "Failed to initialize ADC unit: %s", esp_err_to_name(err));
    }
}

double mq7_read_co_ppm(void)
{
    int raw_val = 0;
    if (s_initialized && s_adc_handle != NULL) {
        esp_err_t err = adc_oneshot_read(s_adc_handle, s_channel, &raw_val);
        if (err == ESP_OK) {
            // Simple mapping: raw ADC value (0-4095) to ppm (0-100 ppm)
            // Voltage range is up to 3.3V (12dB attenuation)
            double volts = (double)raw_val * 3.3 / 4095.0;
            // Let's assume a linear relationship for stubs:
            // 0.1V = 1ppm, 3.0V = 100ppm
            if (volts < 0.1) return 1.0;
            return (volts - 0.1) * (100.0 / 2.9) + 1.0;
        } else {
            ESP_LOGW(TAG, "ADC read failed: %s", esp_err_to_name(err));
        }
    }
    // Fallback to simulated CO level (normal ambient is around 1.5 - 2.5 ppm)
    return 1.8;
}
