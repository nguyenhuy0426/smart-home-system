#include "relay.h"

#include "driver/gpio.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define TAG "RELAY"

static int s_relay_pin = -1;
static int s_active_level = 1;
static uint32_t s_unlock_pulse_ms = 0;
static volatile bool s_unlocked = false;
static bool s_initialized = false;
static TaskHandle_t s_relay_task_handle = NULL;

static int off_level(void)
{
    return s_active_level == 0 ? 1 : 0;
}

void relay_force_off(void)
{
    if (s_relay_pin >= 0) gpio_set_level(s_relay_pin, off_level());
    s_unlocked = false;
}

static void relay_control_task(void *context)
{
    (void)context;
    while (true) {
        ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
        if (!s_initialized) {
            relay_force_off();
            continue;
        }
        gpio_set_level(s_relay_pin, s_active_level);
        s_unlocked = true;
        ESP_LOGI(TAG, "Authorized unlock pulse started");
        vTaskDelay(pdMS_TO_TICKS(s_unlock_pulse_ms));
        relay_force_off();
        ESP_LOGI(TAG, "Relay returned to OFF");
    }
}

bool relay_init(int gpio_pin, int active_level, uint32_t unlock_pulse_ms)
{
    s_initialized = false;
    s_relay_pin = gpio_pin;
    s_active_level = active_level;
    s_unlock_pulse_ms = unlock_pulse_ms;
    if (gpio_pin < 0 || (active_level != 0 && active_level != 1) ||
            unlock_pulse_ms == 0 || unlock_pulse_ms > 10000) {
        return false;
    }

    gpio_reset_pin((gpio_num_t)s_relay_pin);
    gpio_set_level(s_relay_pin, off_level());
    gpio_config_t configuration = {
        .intr_type = GPIO_INTR_DISABLE,
        .mode = GPIO_MODE_OUTPUT,
        .pin_bit_mask = 1ULL << s_relay_pin,
        .pull_down_en = off_level() == 0 ? GPIO_PULLDOWN_ENABLE : GPIO_PULLDOWN_DISABLE,
        .pull_up_en = off_level() == 1 ? GPIO_PULLUP_ENABLE : GPIO_PULLUP_DISABLE,
    };
    if (gpio_config(&configuration) != ESP_OK ||
            gpio_set_level(s_relay_pin, off_level()) != ESP_OK) {
        relay_force_off();
        ESP_LOGE(TAG, "Relay GPIO initialization failed; output remains OFF");
        return false;
    }
    if (xTaskCreate(relay_control_task, "relay_task", 2048, NULL, 5,
            &s_relay_task_handle) != pdPASS) {
        relay_force_off();
        ESP_LOGE(TAG, "Relay task initialization failed; output remains OFF");
        return false;
    }
    s_initialized = true;
    relay_force_off();
    ESP_LOGI(TAG, "Relay initialized in OFF state");
    return true;
}

bool relay_trigger_unlock(void)
{
    if (!s_initialized || s_relay_task_handle == NULL || s_unlocked) return false;
    return xTaskNotifyGive(s_relay_task_handle) != 0;
}

bool relay_is_unlocked(void)
{
    return s_unlocked;
}
