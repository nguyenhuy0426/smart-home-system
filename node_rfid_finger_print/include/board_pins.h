#ifndef BOARD_PINS_H
#define BOARD_PINS_H

/*
 * Centralized pin map for the RFID + fingerprint access node.
 * Board: ESP32-S3-DevKitC-1 (see platformio.ini).
 *
 * Only wiring that is valid and non-conflicting on the ESP32-S3 is applied as
 * an ACTIVE pin below. Requested target pins that need a driver that does not
 * exist yet, or that are double-booked, are recorded as BOARD_TARGET_*
 * constants with a TODO_HW_CONFIRM note and are NOT silently configured.
 */

#include "driver/gpio.h"
#include "driver/spi_master.h"
#include "driver/uart.h"

/* Electric-lock relay. */
#define BOARD_RELAY_GPIO            GPIO_NUM_4
#define BOARD_RELAY_ACTIVE_LEVEL    1
#define BOARD_RELAY_UNLOCK_PULSE_MS 5000

/* RC522 RFID reader over SPI2 (matches target: CS10, MOSI11, SCK12, MISO13). */
#define BOARD_SPI_HOST              SPI2_HOST
#define BOARD_RC522_CS_GPIO         GPIO_NUM_10
#define BOARD_RC522_MOSI_GPIO       GPIO_NUM_11
#define BOARD_RC522_SCK_GPIO        GPIO_NUM_12
#define BOARD_RC522_MISO_GPIO       GPIO_NUM_13
/* Confirmed wiring routes RC522 RST -> GPIO14 (was previously mis-targeted at
 * GPIO9, which collided with the OLED SCL target; that conflict is now gone).
 * The mfrc522 driver in this project performs a SOFT reset over SPI
 * (PCD_SOFT_RESET / 0x0F written to CommandReg inside mfrc522_init) and exposes
 * NO hardware RST pin parameter, so firmware does not drive GPIO14. The pin is
 * recorded here for wiring documentation only and is left unconfigured (the
 * RC522 may hold RST high internally / externally). No hardware-RST API is
 * invented. See the PHASE 03 completion report for RST status. */
#define BOARD_TARGET_RC522_RST_GPIO 14

/* TZM1026 fingerprint module over UART1.
 * Sensor TX_OUT -> GPIO17, which must be the ESP32 RX.
 * Sensor RX_IN  -> GPIO18, which must be the ESP32 TX. */
#define BOARD_TZM_UART              UART_NUM_1
#define BOARD_TZM_ESP_RX_GPIO       GPIO_NUM_17 /* connects to sensor TX_OUT */
#define BOARD_TZM_ESP_TX_GPIO       GPIO_NUM_18 /* connects to sensor RX_IN  */
#define BOARD_TZM_BAUD_RATE         57600       /* existing project baud rate; unchanged */
/* Optional TOUCH_OUT wake line. Finger presence is already detected over UART,
 * so this line is reserved and unused pending hardware confirmation. */
#define BOARD_TZM_TOUCH_OUT_GPIO    GPIO_NUM_15 /* TODO_HW_CONFIRM optional */

/* On-board BOOT button; hold to open the RFID enrollment window. */
#define BOARD_ENROLL_BUTTON_GPIO    GPIO_NUM_0
#define BOARD_ENROLL_BUTTON_HOLD_MS 3000
#define BOARD_ENROLL_WINDOW_MS      30000

/* 0.96" SSD1306 OLED status display over I2C (PHASE 04). SDA->GPIO8, SCL->GPIO9.
 * Driven by the real ESP-IDF esp_lcd SSD1306 panel driver via the new I2C-master
 * driver (esp_driver_i2c). This is the only I2C device on the node (RC522 is SPI,
 * TZM1026 is UART), so the OLED owns the I2C bus exclusively; the module brings the
 * bus up exactly once and a double-init guard prevents re-initialization. GPIO8/9
 * do not conflict with any active pin (RC522 RST target is GPIO14, unused). A
 * display init/render failure is fully isolated and never affects the fail-closed
 * access-control path (see display_ssd1306.h). */
#define BOARD_OLED_SDA_GPIO         GPIO_NUM_8
#define BOARD_OLED_SCL_GPIO         GPIO_NUM_9
#define BOARD_OLED_I2C_PORT         (-1)     /* auto-select a free I2C controller */
#define BOARD_OLED_I2C_ADDR         0x3C     /* standard 0.96" SSD1306 7-bit addr */
#define BOARD_OLED_I2C_SCL_HZ       400000   /* SSD1306 fast-mode I2C */
#define BOARD_OLED_HEIGHT_PX        64       /* 0.96" module is 128x64 */

#endif /* BOARD_PINS_H */
