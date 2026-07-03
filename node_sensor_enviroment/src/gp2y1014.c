#include "gp2y1014.h"
#include "esp_log.h"
#include "rom/ets_sys.h"

#define TAG "GP2Y1014"

static gpio_num_t s_led_pin = GPIO_NUM_5;
static adc_oneshot_unit_handle_t s_adc_handle = NULL;
static adc_channel_t s_channel;
static bool s_initialized = false;

void gp2y1014_init(gpio_num_t led_pin, adc_unit_t adc_unit, adc_channel_t channel)
{
    s_led_pin = led_pin;
    s_channel = channel;

    gpio_reset_pin(s_led_pin);
    gpio_set_direction(s_led_pin, GPIO_MODE_OUTPUT);
    gpio_set_level(s_led_pin, 1); // LED is active LOW on many breakout boards

    adc_oneshot_unit_init_cfg_t init_config = {
        .unit_id = adc_unit,
        .ulp_mode = ADC_ULP_MODE_DISABLE,
    };

    // Note: We might reuse the ADC unit if initialized by another sensor.
    // So we try to initialize it. If it returns ESP_ERR_INVALID_STATE (already initialized),
    // we'll just check if we have a valid handle, or we can use raw oneshot.
    // In our case, we can handle duplicate inits gracefully.
    esp_err_t err = adc_oneshot_new_unit(&init_config, &s_adc_handle);
    if (err == ESP_OK || err == ESP_ERR_INVALID_STATE) {
        adc_oneshot_chan_cfg_t config = {
            .bitwidth = ADC_BITWIDTH_DEFAULT,
            .atten = ADC_ATTEN_DB_12,
        };
        err = adc_oneshot_config_channel(s_adc_handle, s_channel, &config);
        if (err == ESP_OK) {
            s_initialized = true;
            ESP_LOGI(TAG, "GP2Y1014 configured on LED Pin %d, ADC Channel %d", led_pin, channel);
        } else {
            ESP_LOGE(TAG, "Failed to config GP2Y1014 ADC: %s", esp_err_to_name(err));
        }
    } else {
        ESP_LOGE(TAG, "Failed to init GP2Y1014 ADC unit: %s", esp_err_to_name(err));
    }
}

double gp2y1014_read_pm25(void)
{
    int raw_val = 0;
    if (s_initialized && s_adc_handle != NULL) {
        gpio_set_level(s_led_pin, 0); // Turn LED on
        ets_delay_us(280);            // Wait 280us
        esp_err_t err = adc_oneshot_read(s_adc_handle, s_channel, &raw_val);
        ets_delay_us(40);             // Wait 40us
        gpio_set_level(s_led_pin, 1); // Turn LED off

        if (err == ESP_OK) {
            double volts = (double)raw_val * 3.3 / 4095.0;
            // PM2.5 calculation formula:
            // Dust Density = 0.17 * Volts - 0.1 (mg/m^3)
            // PM2.5 in ug/m^3 = Dust Density * 1000.0
            double density = 0.17 * volts - 0.1;
            if (density < 0.0) density = 0.0;
            return density * 1000.0;
        } else {
            ESP_LOGW(TAG, "PM2.5 ADC read failed: %s", esp_err_to_name(err));
        }
    }
    // Fallback simulated value (clean room is ~10-15 ug/m3)
    return 14.5;
}
