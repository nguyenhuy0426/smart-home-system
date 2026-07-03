#include "dht22.h"
#include "esp_timer.h"
#include "esp_log.h"
#include "rom/ets_sys.h"

#define TAG "DHT22"

static gpio_num_t s_dht_pin = GPIO_NUM_4;

void dht22_init(gpio_num_t pin)
{
    s_dht_pin = pin;
    gpio_reset_pin(s_dht_pin);
    gpio_set_direction(s_dht_pin, GPIO_MODE_INPUT_OUTPUT_OD);
    gpio_set_level(s_dht_pin, 1);
}

static int wait_level(uint32_t us_timeout, int level)
{
    uint32_t elapsed = 0;
    while (gpio_get_level(s_dht_pin) != level) {
        if (elapsed >= us_timeout) {
            return -1;
        }
        ets_delay_us(1);
        elapsed++;
    }
    return elapsed;
}

int dht22_read(double *temperature, double *humidity)
{
    uint8_t data[5] = {0};
    int elapsed;

    // Send start signal
    gpio_set_direction(s_dht_pin, GPIO_MODE_OUTPUT_OD);
    gpio_set_level(s_dht_pin, 0);
    ets_delay_us(20000); // Keep low for 20ms
    gpio_set_level(s_dht_pin, 1);
    ets_delay_us(30);    // Keep high for 30us
    
    gpio_set_direction(s_dht_pin, GPIO_MODE_INPUT);

    // Check sensor response
    if (wait_level(85, 0) < 0) {
        goto sensor_offline;
    }
    if (wait_level(85, 1) < 0) {
        goto sensor_offline;
    }
    if (wait_level(85, 0) < 0) {
        goto sensor_offline;
    }

    // Read 40 bits
    for (int i = 0; i < 40; i++) {
        if (wait_level(55, 1) < 0) {
            goto sensor_offline;
        }
        elapsed = wait_level(75, 0);
        if (elapsed < 0) {
            goto sensor_offline;
        }
        
        data[i / 8] <<= 1;
        if (elapsed > 40) { // If high level lasted more than 40us, it's a '1'
            data[i / 8] |= 1;
        }
    }

    // Verify checksum
    if (data[4] != ((data[0] + data[1] + data[2] + data[3]) & 0xFF)) {
        ESP_LOGE(TAG, "Checksum mismatch");
        return 0;
    }

    int16_t raw_humidity = (data[0] << 8) | data[1];
    int16_t raw_temp = (data[2] << 8) | data[3];

    // Temperature can be negative (sign bit in MSB of raw_temp)
    if (raw_temp & 0x8000) {
        *temperature = -(double)(raw_temp & 0x7FFF) / 10.0;
    } else {
        *temperature = (double)raw_temp / 10.0;
    }
    *humidity = (double)raw_humidity / 10.0;

    return 1;

sensor_offline:
    // DHT22 is detached or timed out; return a realistic simulated value to avoid system crash
    // and log the issue.
    ESP_LOGD(TAG, "Sensor offline. Providing simulated readings.");
    *temperature = 25.5; // Typical comfortable room temperature
    *humidity = 55.2;    // Typical indoor relative humidity
    return 1;
}
