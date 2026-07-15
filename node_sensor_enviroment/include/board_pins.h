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

/* Reserved GP2Y1014 LED pin. The absent sensor is never initialized. */
#define BOARD_GP2Y_LED_GPIO         GPIO_NUM_6

/* GP2Y1014 physical presence. The sensor is NOT currently wired, and a
 * floating ADC pin cannot reliably prove presence or absence, so this is
 * an explicit build-time switch instead of auto-detection. It must remain
 * disabled on this assembly: GPIO2/ADC1_CH1 is exclusively owned by MQ7,
 * no GP2Y ADC channel is assigned, GPIO6 is never driven, and PM2.5 is
 * reported as not_connected (no fabricated values). */
#define BOARD_GP2Y_CONNECTED        0

#if BOARD_GP2Y_CONNECTED
#error "GP2Y1014 has no ADC assignment; GPIO2/ADC1_CH1 is exclusively MQ7"
#endif

/* CJMCU-680 / BME680 over I2C: SDA -> GPIO8, SCL -> GPIO9. */
#define BOARD_BME680_SDA_GPIO       GPIO_NUM_8
#define BOARD_BME680_SCL_GPIO       GPIO_NUM_9

/* Analog acquisition unit for the installed MQ7. */
#define BOARD_ADC_UNIT              ADC_UNIT_1

/* MQ-7 CO sensor analog output (AO) -> GPIO2 / ADC1_CH1.
 * The breakout exposes only VCC/GND/AO (no MCU heater control).
 * TODO_HW_CONFIRM: MQ-7 module VCC is 5 V. The AO output swing must be
 * measured on the actual breakout and, if it can exceed the ESP32-S3 ADC
 * input range, an external voltage divider must be added before trusting
 * these readings. Do not assume AO is inherently ADC-safe. */
#define BOARD_MQ7_ADC_GPIO          GPIO_NUM_2
#define BOARD_MQ7_ADC_CHANNEL       ADC_CHANNEL_1

_Static_assert(BOARD_MQ7_ADC_GPIO == GPIO_NUM_2 &&
        BOARD_MQ7_ADC_CHANNEL == ADC_CHANNEL_1,
        "MQ7 wiring contract requires GPIO2/ADC1_CH1");

/* ST7789 1.3" 240x240 SPI display on SPI2_HOST.
 * HW_CONFIRMED wiring: SDA->GPIO11, SCL->GPIO12, RES->GPIO14, DC->GPIO13,
 * BLK->GPIO15. The module has NO CS pad (CS tied low on-board).
 *  - CS = GPIO_NUM_NC (-1): esp_lcd passes cs_gpio_num straight into
 *    spi_device_interface_config_t.spics_io_num, where -1 is the documented
 *    "no CS" value. Such modules need SPI mode 3 instead of mode 0
 *    (see display_st7789.c).
 *  - RST = GPIO14 (RES pad IS wired): with CS tied low the panel's serial
 *    bit counter can only be resynchronized by a hardware RESX pulse, so
 *    the wired reset line MUST be driven. Leaving it floating holds the
 *    panel in reset and every init command (including SWRESET) is lost. */
#define BOARD_ST7789_SPI_HOST       SPI2_HOST
#define BOARD_ST7789_CS_GPIO        GPIO_NUM_NC /* module has no CS pad */
#define BOARD_ST7789_MOSI_GPIO      GPIO_NUM_11 /* module SDA */
#define BOARD_ST7789_SCLK_GPIO      GPIO_NUM_12 /* module SCL */
#define BOARD_ST7789_DC_GPIO        GPIO_NUM_13
#define BOARD_ST7789_RST_GPIO       GPIO_NUM_14 /* module RES, active low */
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
    ESP_LOGI("BOARD_PINS", "active map: MQ7=GPIO%d/ADC1_CH%d "
            "BME680=SDA%d/SCL%d ST7789=MOSI%d/SCLK%d/DC%d/RST%d/BL%d "
            "GP2Y=disabled DHT22=removed",
            BOARD_MQ7_ADC_GPIO, BOARD_MQ7_ADC_CHANNEL,
            BOARD_BME680_SDA_GPIO, BOARD_BME680_SCL_GPIO,
            BOARD_ST7789_MOSI_GPIO, BOARD_ST7789_SCLK_GPIO,
            BOARD_ST7789_DC_GPIO, BOARD_ST7789_RST_GPIO,
            BOARD_ST7789_BL_GPIO);
    ESP_LOGW("BOARD_PINS",
            "MQ-7 AO on ADC1_CH%d (GPIO%d) is driven from a 5 V module; verify "
            "the real AO swing and add a divider if it exceeds the ADC input "
            "range (TODO_HW_CONFIRM).",
            BOARD_MQ7_ADC_CHANNEL, BOARD_MQ7_ADC_GPIO);
}

#endif /* BOARD_PINS_H */
