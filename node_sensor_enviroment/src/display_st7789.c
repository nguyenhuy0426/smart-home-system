#include "display_st7789.h"

#include "board_pins.h"
#include "sensor_status.h"

#include "driver/gpio.h"
#include "driver/spi_master.h"
#include "esp_heap_caps.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_vendor.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include <stdio.h>
#include <string.h>

#define TAG "DISPLAY_ST7789"

/* Panel geometry: 240x240, rendered as a 15x15 grid of 16x16 text cells
 * (8x8 public-domain font scaled 2x). Only STATUS_ROWS rows are used. */
#define DISPLAY_WIDTH_PX 240
#define DISPLAY_HEIGHT_PX 240
#define FONT_WIDTH_PX 8
#define FONT_HEIGHT_PX 8
#define FONT_SCALE 2
#define CELL_WIDTH_PX (FONT_WIDTH_PX * FONT_SCALE)
#define CELL_HEIGHT_PX (FONT_HEIGHT_PX * FONT_SCALE)
#define TEXT_COLUMNS (DISPLAY_WIDTH_PX / CELL_WIDTH_PX)
#define TEXT_ROWS (DISPLAY_HEIGHT_PX / CELL_HEIGHT_PX)
#define STATUS_ROWS 9

/* One text row worth of RGB565 pixels, sent per esp_lcd draw call. */
#define STRIPE_PIXELS (DISPLAY_WIDTH_PX * CELL_HEIGHT_PX)
#define STRIPE_BYTES (STRIPE_PIXELS * 2)

#define DISPLAY_SPI_CLOCK_HZ (20 * 1000 * 1000)
#define DISPLAY_TRANS_QUEUE_DEPTH 10
#define DISPLAY_DMA_WAIT_TICKS pdMS_TO_TICKS(1000)
/* Log the first draw failure, then only every Nth, to avoid log spam
 * when the panel is wired badly but init happened to succeed. */
#define DISPLAY_ERROR_LOG_PERIOD 60

/* RGB565 */
#define COLOR_BLACK 0x0000u
#define COLOR_WHITE 0xFFFFu
#define COLOR_RED 0xF800u

/*
 * 8x8 glyphs for ASCII 0x20..0x5F, from the public-domain font8x8_basic
 * set (https://github.com/dhepper/font8x8). Bit 0 of each row byte is the
 * leftmost pixel. Lowercase input is mapped to uppercase at lookup time.
 */
static const uint8_t s_font8x8[0x60 - 0x20][FONT_HEIGHT_PX] = {
    {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, /* space */
    {0x18, 0x3C, 0x3C, 0x18, 0x18, 0x00, 0x18, 0x00}, /* ! */
    {0x36, 0x36, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, /* " */
    {0x36, 0x36, 0x7F, 0x36, 0x7F, 0x36, 0x36, 0x00}, /* # */
    {0x0C, 0x3E, 0x03, 0x1E, 0x30, 0x1F, 0x0C, 0x00}, /* $ */
    {0x00, 0x63, 0x33, 0x18, 0x0C, 0x66, 0x63, 0x00}, /* % */
    {0x1C, 0x36, 0x1C, 0x6E, 0x3B, 0x33, 0x6E, 0x00}, /* & */
    {0x06, 0x06, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00}, /* ' */
    {0x18, 0x0C, 0x06, 0x06, 0x06, 0x0C, 0x18, 0x00}, /* ( */
    {0x06, 0x0C, 0x18, 0x18, 0x18, 0x0C, 0x06, 0x00}, /* ) */
    {0x00, 0x66, 0x3C, 0xFF, 0x3C, 0x66, 0x00, 0x00}, /* * */
    {0x00, 0x0C, 0x0C, 0x3F, 0x0C, 0x0C, 0x00, 0x00}, /* + */
    {0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C, 0x06}, /* , */
    {0x00, 0x00, 0x00, 0x3F, 0x00, 0x00, 0x00, 0x00}, /* - */
    {0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C, 0x00}, /* . */
    {0x60, 0x30, 0x18, 0x0C, 0x06, 0x03, 0x01, 0x00}, /* / */
    {0x3E, 0x63, 0x73, 0x7B, 0x6F, 0x67, 0x3E, 0x00}, /* 0 */
    {0x0C, 0x0E, 0x0C, 0x0C, 0x0C, 0x0C, 0x3F, 0x00}, /* 1 */
    {0x1E, 0x33, 0x30, 0x1C, 0x06, 0x33, 0x3F, 0x00}, /* 2 */
    {0x1E, 0x33, 0x30, 0x1C, 0x30, 0x33, 0x1E, 0x00}, /* 3 */
    {0x38, 0x3C, 0x36, 0x33, 0x7F, 0x30, 0x78, 0x00}, /* 4 */
    {0x3F, 0x03, 0x1F, 0x30, 0x30, 0x33, 0x1E, 0x00}, /* 5 */
    {0x1C, 0x06, 0x03, 0x1F, 0x33, 0x33, 0x1E, 0x00}, /* 6 */
    {0x3F, 0x33, 0x30, 0x18, 0x0C, 0x0C, 0x0C, 0x00}, /* 7 */
    {0x1E, 0x33, 0x33, 0x1E, 0x33, 0x33, 0x1E, 0x00}, /* 8 */
    {0x1E, 0x33, 0x33, 0x3E, 0x30, 0x18, 0x0E, 0x00}, /* 9 */
    {0x00, 0x0C, 0x0C, 0x00, 0x00, 0x0C, 0x0C, 0x00}, /* : */
    {0x00, 0x0C, 0x0C, 0x00, 0x00, 0x0C, 0x0C, 0x06}, /* ; */
    {0x18, 0x0C, 0x06, 0x03, 0x06, 0x0C, 0x18, 0x00}, /* < */
    {0x00, 0x00, 0x3F, 0x00, 0x00, 0x3F, 0x00, 0x00}, /* = */
    {0x06, 0x0C, 0x18, 0x30, 0x18, 0x0C, 0x06, 0x00}, /* > */
    {0x1E, 0x33, 0x30, 0x18, 0x0C, 0x00, 0x0C, 0x00}, /* ? */
    {0x3E, 0x63, 0x7B, 0x7B, 0x7B, 0x03, 0x1E, 0x00}, /* @ */
    {0x0C, 0x1E, 0x33, 0x33, 0x3F, 0x33, 0x33, 0x00}, /* A */
    {0x3F, 0x66, 0x66, 0x3E, 0x66, 0x66, 0x3F, 0x00}, /* B */
    {0x3C, 0x66, 0x03, 0x03, 0x03, 0x66, 0x3C, 0x00}, /* C */
    {0x1F, 0x36, 0x66, 0x66, 0x66, 0x36, 0x1F, 0x00}, /* D */
    {0x7F, 0x46, 0x16, 0x1E, 0x16, 0x46, 0x7F, 0x00}, /* E */
    {0x7F, 0x46, 0x16, 0x1E, 0x16, 0x06, 0x0F, 0x00}, /* F */
    {0x3C, 0x66, 0x03, 0x03, 0x73, 0x66, 0x7C, 0x00}, /* G */
    {0x33, 0x33, 0x33, 0x3F, 0x33, 0x33, 0x33, 0x00}, /* H */
    {0x1E, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x1E, 0x00}, /* I */
    {0x78, 0x30, 0x30, 0x30, 0x33, 0x33, 0x1E, 0x00}, /* J */
    {0x67, 0x66, 0x36, 0x1E, 0x36, 0x66, 0x67, 0x00}, /* K */
    {0x0F, 0x06, 0x06, 0x06, 0x46, 0x66, 0x7F, 0x00}, /* L */
    {0x63, 0x77, 0x7F, 0x7F, 0x6B, 0x63, 0x63, 0x00}, /* M */
    {0x63, 0x67, 0x6F, 0x7B, 0x73, 0x63, 0x63, 0x00}, /* N */
    {0x1C, 0x36, 0x63, 0x63, 0x63, 0x36, 0x1C, 0x00}, /* O */
    {0x3F, 0x66, 0x66, 0x3E, 0x06, 0x06, 0x0F, 0x00}, /* P */
    {0x1E, 0x33, 0x33, 0x33, 0x3B, 0x1E, 0x38, 0x00}, /* Q */
    {0x3F, 0x66, 0x66, 0x3E, 0x36, 0x66, 0x67, 0x00}, /* R */
    {0x1E, 0x33, 0x07, 0x0E, 0x38, 0x33, 0x1E, 0x00}, /* S */
    {0x3F, 0x2D, 0x0C, 0x0C, 0x0C, 0x0C, 0x1E, 0x00}, /* T */
    {0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x3F, 0x00}, /* U */
    {0x33, 0x33, 0x33, 0x33, 0x33, 0x1E, 0x0C, 0x00}, /* V */
    {0x63, 0x63, 0x63, 0x6B, 0x7F, 0x77, 0x63, 0x00}, /* W */
    {0x63, 0x63, 0x36, 0x1C, 0x1C, 0x36, 0x63, 0x00}, /* X */
    {0x33, 0x33, 0x33, 0x1E, 0x0C, 0x0C, 0x1E, 0x00}, /* Y */
    {0x7F, 0x63, 0x31, 0x18, 0x4C, 0x66, 0x7F, 0x00}, /* Z */
    {0x1E, 0x06, 0x06, 0x06, 0x06, 0x06, 0x1E, 0x00}, /* [ */
    {0x03, 0x06, 0x0C, 0x18, 0x30, 0x60, 0x40, 0x00}, /* backslash */
    {0x1E, 0x18, 0x18, 0x18, 0x18, 0x18, 0x1E, 0x00}, /* ] */
    {0x08, 0x1C, 0x36, 0x63, 0x00, 0x00, 0x00, 0x00}, /* ^ */
    {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF}, /* _ */
};

static bool s_init_attempted;
static bool s_ready;
static bool s_spi_bus_owned;
static esp_lcd_panel_io_handle_t s_panel_io;
static esp_lcd_panel_handle_t s_panel;
static uint16_t *s_stripe; /* DMA-capable one-text-row pixel buffer */
static SemaphoreHandle_t s_stripe_free; /* held while DMA reads s_stripe */
static char s_line_cache[STATUS_ROWS][TEXT_COLUMNS + 1];
static uint16_t s_line_color_cache[STATUS_ROWS];
static uint32_t s_draw_error_count;

/* ISR context: DMA finished reading s_stripe; hand it back to the CPU. */
static bool on_color_trans_done(esp_lcd_panel_io_handle_t panel_io,
        esp_lcd_panel_io_event_data_t *event_data, void *user_ctx)
{
    (void)panel_io;
    (void)event_data;
    (void)user_ctx;
    BaseType_t higher_priority_woken = pdFALSE;
    xSemaphoreGiveFromISR(s_stripe_free, &higher_priority_woken);
    return higher_priority_woken == pdTRUE;
}

static const uint8_t *glyph_for(char character)
{
    if (character >= 'a' && character <= 'z') {
        character = (char)(character - 'a' + 'A');
    }
    if (character < 0x20 || character > 0x5F) {
        character = '?';
    }
    return s_font8x8[character - 0x20];
}

static void stripe_fill(uint16_t color)
{
    for (size_t i = 0; i < STRIPE_PIXELS; ++i) {
        s_stripe[i] = color;
    }
}

/* Rasterize up to TEXT_COLUMNS characters into the stripe buffer. */
static void stripe_draw_text(const char *text, uint16_t color)
{
    stripe_fill(COLOR_BLACK);
    for (int column = 0;
            column < TEXT_COLUMNS && text[column] != '\0'; ++column) {
        const uint8_t *glyph = glyph_for(text[column]);
        for (int glyph_y = 0; glyph_y < FONT_HEIGHT_PX; ++glyph_y) {
            uint8_t row_bits = glyph[glyph_y];
            if (row_bits == 0) continue;
            for (int glyph_x = 0; glyph_x < FONT_WIDTH_PX; ++glyph_x) {
                if ((row_bits & (1u << glyph_x)) == 0) continue;
                int base_x = column * CELL_WIDTH_PX + glyph_x * FONT_SCALE;
                int base_y = glyph_y * FONT_SCALE;
                for (int scale_y = 0; scale_y < FONT_SCALE; ++scale_y) {
                    uint16_t *pixel_row =
                            &s_stripe[(base_y + scale_y) * DISPLAY_WIDTH_PX];
                    for (int scale_x = 0; scale_x < FONT_SCALE; ++scale_x) {
                        pixel_row[base_x + scale_x] = color;
                    }
                }
            }
        }
    }
}

static void log_draw_failure_throttled(const char *what, esp_err_t error)
{
    if (s_draw_error_count % DISPLAY_ERROR_LOG_PERIOD == 0) {
        ESP_LOGW(TAG, "%s failed: %s (occurrence %u)", what,
                esp_err_to_name(error), (unsigned)(s_draw_error_count + 1));
    }
    ++s_draw_error_count;
}

/* Send the stripe buffer to one 16-px text row. Caller must hold
 * s_stripe_free; ownership passes to DMA on success (returned by the
 * on_color_trans_done ISR) and back to the caller pool on failure. */
static bool stripe_present(int text_row)
{
    esp_err_t error = esp_lcd_panel_draw_bitmap(s_panel,
            0, text_row * CELL_HEIGHT_PX,
            DISPLAY_WIDTH_PX, (text_row + 1) * CELL_HEIGHT_PX,
            s_stripe);
    if (error != ESP_OK) {
        xSemaphoreGive(s_stripe_free); /* DMA never started */
        log_draw_failure_throttled("draw_bitmap", error);
        return false;
    }
    return true;
}

/* Redraw one text row if its content or color changed since last render. */
static void render_line(int text_row, const char *text, uint16_t color)
{
    if (text_row < 0 || text_row >= STATUS_ROWS) return;
    if (s_line_color_cache[text_row] == color &&
            strncmp(s_line_cache[text_row], text, TEXT_COLUMNS) == 0) {
        return;
    }
    if (xSemaphoreTake(s_stripe_free, DISPLAY_DMA_WAIT_TICKS) != pdTRUE) {
        log_draw_failure_throttled("stripe wait", ESP_ERR_TIMEOUT);
        return;
    }
    stripe_draw_text(text, color);
    if (stripe_present(text_row)) {
        (void)snprintf(s_line_cache[text_row],
                sizeof(s_line_cache[text_row]), "%s", text);
        s_line_color_cache[text_row] = color;
    }
}

/* Release everything init allocated, in reverse dependency order.
 * Only called with no DMA transfer in flight (take-before-fill protocol
 * guarantees at most one transfer, completed before the next take). */
static void display_teardown(void)
{
    if (s_panel != NULL) {
        (void)esp_lcd_panel_del(s_panel);
        s_panel = NULL;
    }
    if (s_panel_io != NULL) {
        (void)esp_lcd_panel_io_del(s_panel_io);
        s_panel_io = NULL;
    }
    if (s_spi_bus_owned) {
        (void)spi_bus_free(BOARD_ST7789_SPI_HOST);
        s_spi_bus_owned = false;
    }
    if (s_stripe != NULL) {
        heap_caps_free(s_stripe);
        s_stripe = NULL;
    }
    if (s_stripe_free != NULL) {
        vSemaphoreDelete(s_stripe_free);
        s_stripe_free = NULL;
    }
}

static bool init_failed(const char *step, esp_err_t error)
{
    ESP_LOGE(TAG, "%s failed: %s; display disabled (sensor pipeline "
            "unaffected)", step, esp_err_to_name(error));
    display_teardown();
    return false;
}

bool display_st7789_init(void)
{
    if (s_init_attempted) {
        ESP_LOGW(TAG, "init called more than once; ignoring "
                "(SPI bus must not be re-initialized)");
        return s_ready;
    }
    s_init_attempted = true;

    /* Backlight low during panel bring-up to hide garbage frames. */
    gpio_config_t backlight_config = {
        .pin_bit_mask = 1ULL << BOARD_ST7789_BL_GPIO,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    esp_err_t error = gpio_config(&backlight_config);
    if (error != ESP_OK) return init_failed("backlight gpio_config", error);
    (void)gpio_set_level(BOARD_ST7789_BL_GPIO, 0);

    s_stripe_free = xSemaphoreCreateBinary();
    if (s_stripe_free == NULL) {
        return init_failed("stripe semaphore create", ESP_ERR_NO_MEM);
    }
    (void)xSemaphoreGive(s_stripe_free); /* CPU owns the buffer initially */

    s_stripe = heap_caps_malloc(STRIPE_BYTES, MALLOC_CAP_DMA);
    if (s_stripe == NULL) {
        return init_failed("DMA stripe alloc", ESP_ERR_NO_MEM);
    }

    spi_bus_config_t bus_config = {
        .mosi_io_num = BOARD_ST7789_MOSI_GPIO,
        .miso_io_num = -1,
        .sclk_io_num = BOARD_ST7789_SCLK_GPIO,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
        .max_transfer_sz = STRIPE_BYTES,
    };
    error = spi_bus_initialize(BOARD_ST7789_SPI_HOST, &bus_config,
            SPI_DMA_CH_AUTO);
    if (error != ESP_OK) return init_failed("spi_bus_initialize", error);
    s_spi_bus_owned = true;

    esp_lcd_panel_io_spi_config_t io_config = {
        /* TODO_HW_CONFIRM: if the physical module has no CS pad, set
         * BOARD_ST7789_CS_GPIO to -1 in board_pins.h; such modules also
         * commonly require spi_mode = 3 instead of 0. I am not sure which
         * variant is installed until the hardware is inspected. */
        .cs_gpio_num = BOARD_ST7789_CS_GPIO,
        .dc_gpio_num = BOARD_ST7789_DC_GPIO,
        .spi_mode = 0,
        .pclk_hz = DISPLAY_SPI_CLOCK_HZ,
        .trans_queue_depth = DISPLAY_TRANS_QUEUE_DEPTH,
        .on_color_trans_done = on_color_trans_done,
        .user_ctx = NULL,
        .lcd_cmd_bits = 8,
        .lcd_param_bits = 8,
    };
    error = esp_lcd_new_panel_io_spi(
            (esp_lcd_spi_bus_handle_t)BOARD_ST7789_SPI_HOST,
            &io_config, &s_panel_io);
    if (error != ESP_OK) return init_failed("esp_lcd_new_panel_io_spi", error);

    esp_lcd_panel_dev_config_t panel_config = {
        .reset_gpio_num = BOARD_ST7789_RST_GPIO,
        .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
        /* Little-endian RGB565 handled by the ST7789 RAMCTRL register in
         * the IDF 5.3 driver, so no software byte-swap is needed. */
        .data_endian = LCD_RGB_DATA_ENDIAN_LITTLE,
        .bits_per_pixel = 16,
    };
    error = esp_lcd_new_panel_st7789(s_panel_io, &panel_config, &s_panel);
    if (error != ESP_OK) return init_failed("esp_lcd_new_panel_st7789", error);

    error = esp_lcd_panel_reset(s_panel);
    if (error != ESP_OK) return init_failed("panel reset", error);
    error = esp_lcd_panel_init(s_panel);
    if (error != ESP_OK) return init_failed("panel init", error);

    /* TODO_HW_CONFIRM: 1.3" IPS 240x240 ST7789 modules commonly need
     * color inversion enabled; flip to false if colors render inverted
     * on the real panel. */
    error = esp_lcd_panel_invert_color(s_panel, true);
    if (error != ESP_OK) return init_failed("invert_color", error);

    error = esp_lcd_panel_disp_on_off(s_panel, true);
    if (error != ESP_OK) return init_failed("disp_on_off", error);

    /* Clear the full panel to black before enabling the backlight. */
    for (int text_row = 0; text_row < TEXT_ROWS; ++text_row) {
        if (xSemaphoreTake(s_stripe_free, DISPLAY_DMA_WAIT_TICKS) != pdTRUE) {
            return init_failed("clear-screen stripe wait", ESP_ERR_TIMEOUT);
        }
        stripe_fill(COLOR_BLACK);
        error = esp_lcd_panel_draw_bitmap(s_panel,
                0, text_row * CELL_HEIGHT_PX,
                DISPLAY_WIDTH_PX, (text_row + 1) * CELL_HEIGHT_PX,
                s_stripe);
        if (error != ESP_OK) {
            (void)xSemaphoreGive(s_stripe_free);
            return init_failed("clear-screen draw", error);
        }
    }

    /* TODO_HW_CONFIRM: BLK assumed active-high; invert if the panel stays
     * dark with everything else working. */
    (void)gpio_set_level(BOARD_ST7789_BL_GPIO, 1);

    s_ready = true;
    ESP_LOGI(TAG, "ST7789 240x240 ready on SPI2 (MOSI=%d SCLK=%d DC=%d "
            "RST=%d CS=%d BL=%d)",
            BOARD_ST7789_MOSI_GPIO, BOARD_ST7789_SCLK_GPIO,
            BOARD_ST7789_DC_GPIO, BOARD_ST7789_RST_GPIO,
            BOARD_ST7789_CS_GPIO, BOARD_ST7789_BL_GPIO);
    return true;
}

static uint16_t status_color(sensor_status_t status)
{
    return status == SENSOR_STATUS_VALID ? COLOR_WHITE : COLOR_RED;
}

void display_st7789_render_sample(
        const environment_raw_sensor_sample_t *sample, bool wifi_connected)
{
    if (!s_ready) return;

    /* Scratch is wider than a display row; render_line/caching clamp to
     * TEXT_COLUMNS, so snprintf truncation here is harmless. */
    char line[48];

    (void)snprintf(line, sizeof(line), "%s",
            wifi_connected ? "ENV  WIFI UP" : "ENV  WIFI DOWN");
    render_line(0, line, wifi_connected ? COLOR_WHITE : COLOR_RED);

    if (sample == NULL) {
        /* Offline heartbeat: keep the last real values on screen and
         * never substitute placeholders. */
        return;
    }

    if (sample->dht22_status == SENSOR_STATUS_VALID) {
        (void)snprintf(line, sizeof(line), "T %.1fC H %.0f%%",
                sample->dht22_temperature_degc,
                sample->dht22_humidity_percent);
    } else {
        (void)snprintf(line, sizeof(line), "DHT %s",
                sensor_status_name(sample->dht22_status));
    }
    render_line(1, line, status_color(sample->dht22_status));

    if (sample->bme680_status == SENSOR_STATUS_VALID) {
        (void)snprintf(line, sizeof(line), "B %.1fC H %.0f%%",
                sample->bme680_temperature_degc,
                sample->bme680_humidity_percent);
    } else {
        (void)snprintf(line, sizeof(line), "BME %s",
                sensor_status_name(sample->bme680_status));
    }
    render_line(2, line, status_color(sample->bme680_status));

    if (sample->bme680_status == SENSOR_STATUS_VALID) {
        (void)snprintf(line, sizeof(line), "P %.1f HPA", sample->pressure_hpa);
    } else {
        (void)snprintf(line, sizeof(line), "P ---");
    }
    render_line(3, line, status_color(sample->bme680_status));

    if (sample->bme680_gas_status == SENSOR_STATUS_VALID) {
        (void)snprintf(line, sizeof(line), "G %.1f KOHM",
                sample->gas_resistance_ohm / 1000.0);
    } else {
        (void)snprintf(line, sizeof(line), "G %s",
                sensor_status_name(sample->bme680_gas_status));
    }
    render_line(4, line, status_color(sample->bme680_gas_status));

    switch (sample->mq7_status) {
    case SENSOR_STATUS_VALID:
        (void)snprintf(line, sizeof(line), "CO %.1f PPM", sample->mq7_co_ppm);
        break;
    case SENSOR_STATUS_CALIBRATION_MISSING:
        /* Real ADC millivolts, honestly labeled as uncalibrated. */
        (void)snprintf(line, sizeof(line), "CO RAW %d MV",
                sample->mq7_adc_millivolts);
        break;
    case SENSOR_STATUS_HEATER_WARMUP:
        (void)snprintf(line, sizeof(line), "CO WARMUP");
        break;
    default:
        (void)snprintf(line, sizeof(line), "CO %s",
                sensor_status_name(sample->mq7_status));
        break;
    }
    render_line(5, line, status_color(sample->mq7_status));

    switch (sample->gp2y_status) {
    case SENSOR_STATUS_VALID:
        (void)snprintf(line, sizeof(line), "PM25 %.1f UG", sample->pm25_ug_m3);
        break;
    case SENSOR_STATUS_CALIBRATION_MISSING:
        (void)snprintf(line, sizeof(line), "PM RAW %d MV",
                sample->gp2y_adc_millivolts);
        break;
    default:
        (void)snprintf(line, sizeof(line), "PM25 %s",
                sensor_status_name(sample->gp2y_status));
        break;
    }
    render_line(6, line, status_color(sample->gp2y_status));

    (void)snprintf(line, sizeof(line), "UP %llu S",
            (unsigned long long)(sample->observed_at_uptime_ms / 1000ULL));
    render_line(7, line, COLOR_WHITE);

    (void)snprintf(line, sizeof(line), "SEQ %llu",
            (unsigned long long)sample->sequence);
    render_line(8, line, COLOR_WHITE);
}
