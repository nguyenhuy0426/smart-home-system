#ifndef BOARD_PINS_H
#define BOARD_PINS_H

/*
 * Centralized pin map for the environment sensor node.
 * Board: ESP32-S3-DevKitC-1 (see platformio.ini).
 *
 * All pins below are the confirmed target wiring and are valid /
 * non-conflicting on the ESP32-S3.
 *
 * ESP32-S3 ADC1 mapping (datasheet): GPIO1..GPIO10 == ADC1_CH0..ADC1_CH9.
 * GPIO35 is NOT ADC-capable on the ESP32-S3.
 */

#include "driver/gpio.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_log.h"
#include "hal/spi_types.h"

/* ---- Confirmed environment-node wiring ---- */

/* DHT22 DATA -> GPIO4. */
#define BOARD_DHT22_DATA_GPIO       GPIO_NUM_4

/* GP2Y1014 dust-sensor pulsed IR LED -> GPIO6. */
#define BOARD_GP2Y_LED_GPIO         GPIO_NUM_6

/* CJMCU-680 / BME680 over I2C: SDA -> GPIO8, SCL -> GPIO9. */
#define BOARD_BME680_SDA_GPIO       GPIO_NUM_8
#define BOARD_BME680_SCL_GPIO       GPIO_NUM_9

/* Analog acquisition unit for both analog sensors. */
#define BOARD_ADC_UNIT              ADC_UNIT_1

/* MQ-7 CO sensor analog output (AO) -> GPIO1 / ADC1_CH0.
 * The breakout exposes only VCC/GND/AO (no MCU heater control).
 * TODO_HW_CONFIRM: MQ-7 module VCC is 5 V. The AO output swing must be
 * measured on the actual breakout and, if it can exceed the ESP32-S3 ADC
 * input range, an external voltage divider must be added before trusting
 * these readings. Do not assume AO is inherently ADC-safe. */
#define BOARD_MQ7_ADC_CHANNEL       ADC_CHANNEL_0 /* ADC1_CH0 == GPIO1 */

/* GP2Y1014 dust-sensor analog output -> GPIO2 / ADC1_CH1. */
#define BOARD_GP2Y_ADC_CHANNEL      ADC_CHANNEL_1 /* ADC1_CH1 == GPIO2 */

/* ST7789 1.3" 240x240 SPI display on SPI2_HOST.
 * CS: TODO_HW_CONFIRM — many 1.3" 240x240 ST7789 breakouts expose no CS pad.
 * If the physical module has a CS pin it is wired to GPIO10 (this default);
 * if it has none, set BOARD_ST7789_CS_GPIO to -1 (real esp_lcd no-CS support
 * via esp_lcd_panel_io_spi_config_t.cs_gpio_num = -1). Note: no-CS modules
 * commonly need SPI mode 3 instead of mode 0 (see display_st7789.c). */
#define BOARD_ST7789_SPI_HOST       SPI2_HOST
#define BOARD_ST7789_CS_GPIO        GPIO_NUM_10 /* TODO_HW_CONFIRM: -1 if module has no CS pad */
#define BOARD_ST7789_MOSI_GPIO      GPIO_NUM_11 /* module SDA */
#define BOARD_ST7789_SCLK_GPIO      GPIO_NUM_12 /* module SCL */
#define BOARD_ST7789_DC_GPIO        GPIO_NUM_13
#define BOARD_ST7789_RST_GPIO       GPIO_NUM_14 /* module RES */
#define BOARD_ST7789_BL_GPIO        GPIO_NUM_15 /* module BLK, assumed active-high (TODO_HW_CONFIRM) */

/*
 * Returns the ESP32-S3 ADC1 channel for a GPIO, or -1 if the pin is not
 * ADC1-capable. ESP32-S3 ADC1 covers GPIO1..GPIO10 (ADC1_CH0..CH9).
 */
static inline int board_pins_adc1_channel_for_gpio(int gpio)
{
    if (gpio >= 1 && gpio <= 10) {
        return gpio - 1;
    }
    return -1;
}

/* True when an adc_channel_t value is a real ADC1 channel on this MCU. */
static inline bool board_pins_is_valid_adc1_channel(int channel)
{
    return channel >= ADC_CHANNEL_0 && channel <= ADC_CHANNEL_9;
}

/* Boot-time sanity log for the analog wiring assumptions. */
static inline void board_pins_report_conflicts(void)
{
    if (BOARD_MQ7_ADC_CHANNEL == BOARD_GP2Y_ADC_CHANNEL) {
        ESP_LOGE("BOARD_PINS",
                "MQ-7 AO and GP2Y analog map to the same ADC1 channel (%d); "
                "analog readings will collide.",
                BOARD_MQ7_ADC_CHANNEL);
    }
    ESP_LOGW("BOARD_PINS",
            "MQ-7 AO on ADC1_CH%d (GPIO1) is driven from a 5 V module; verify "
            "the real AO swing and add a divider if it exceeds the ADC input "
            "range (TODO_HW_CONFIRM).",
            BOARD_MQ7_ADC_CHANNEL);
}

#endif /* BOARD_PINS_H */
