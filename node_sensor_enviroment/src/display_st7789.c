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
#include "freertos/task.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define TAG "DISPLAY_ST7789"

/* Panel geometry and a bounded DMA band. The scene is rasterized repeatedly
 * into this small band, so the UI can use multiple type scales without
 * retaining a 115 KiB full-screen framebuffer beside the BLE stack. */
#define DISPLAY_WIDTH_PX 240
#define DISPLAY_HEIGHT_PX 240
#define FONT_WIDTH_PX 8
#define FONT_HEIGHT_PX 8
#define DISPLAY_BAND_HEIGHT_PX 24
#define STRIPE_PIXELS (DISPLAY_WIDTH_PX * DISPLAY_BAND_HEIGHT_PX)
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
#define COLOR_GREEN 0x07E0u
#define COLOR_CYAN 0x07FFu
#define COLOR_BLUE 0x001Fu
#define COLOR_AMBER 0xFD20u
#define COLOR_DARK_MUTED 0x9CD3u
#define COLOR_DARK_DIVIDER 0x39E7u
#define COLOR_LIGHT_MUTED 0x632Cu
#define COLOR_LIGHT_DIVIDER 0xC618u

typedef struct {
    uint16_t background;
    uint16_t foreground;
    uint16_t muted;
    uint16_t divider;
    uint16_t accent;
    uint16_t ok;
    uint16_t warning;
    uint16_t error;
    const char *name;
} display_palette_t;

static const display_palette_t DARK_PALETTE = {
    .background = COLOR_BLACK,
    .foreground = COLOR_WHITE,
    .muted = COLOR_DARK_MUTED,
    .divider = COLOR_DARK_DIVIDER,
    .accent = COLOR_CYAN,
    .ok = COLOR_GREEN,
    .warning = COLOR_AMBER,
    .error = COLOR_RED,
    .name = "DARK",
};

static const display_palette_t LIGHT_PALETTE = {
    .background = COLOR_WHITE,
    .foreground = COLOR_BLACK,
    .muted = COLOR_LIGHT_MUTED,
    .divider = COLOR_LIGHT_DIVIDER,
    .accent = COLOR_BLUE,
    .ok = 0x04C0u,
    .warning = 0xC400u,
    .error = 0xD800u,
    .name = "LIGHT",
};

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
static uint16_t *s_stripe; /* DMA-capable 240x24 pixel band */
static SemaphoreHandle_t s_stripe_free; /* held while DMA reads s_stripe */
static uint32_t s_draw_error_count;
static uint32_t s_render_count;
static int s_band_y;
static int s_band_height;
static display_st7789_theme_t s_theme = DISPLAY_ST7789_THEME_DARK;

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

static void band_put_pixel(int x, int y, uint16_t color)
{
    if (x < 0 || x >= DISPLAY_WIDTH_PX || y < s_band_y ||
            y >= s_band_y + s_band_height) return;
    s_stripe[(y - s_band_y) * DISPLAY_WIDTH_PX + x] = color;
}

static void fill_rect(int x, int y, int width, int height, uint16_t color)
{
    int x0 = x < 0 ? 0 : x;
    int x1 = x + width > DISPLAY_WIDTH_PX ? DISPLAY_WIDTH_PX : x + width;
    int y0 = y < s_band_y ? s_band_y : y;
    int y1 = y + height > s_band_y + s_band_height
            ? s_band_y + s_band_height : y + height;
    if (x0 >= x1 || y0 >= y1) return;
    for (int pixel_y = y0; pixel_y < y1; ++pixel_y) {
        uint16_t *row = &s_stripe[(pixel_y - s_band_y) * DISPLAY_WIDTH_PX];
        for (int pixel_x = x0; pixel_x < x1; ++pixel_x) row[pixel_x] = color;
    }
}

static void draw_text(int x, int y, const char *text, int scale,
        uint16_t color)
{
    if (text == NULL || scale < 1 || scale > 4) return;
    for (size_t column = 0; text[column] != '\0'; ++column) {
        int character_x = x + (int)column * FONT_WIDTH_PX * scale;
        if (character_x >= DISPLAY_WIDTH_PX) break;
        const uint8_t *glyph = glyph_for(text[column]);
        for (int glyph_y = 0; glyph_y < FONT_HEIGHT_PX; ++glyph_y) {
            uint8_t row_bits = glyph[glyph_y];
            for (int glyph_x = 0; glyph_x < FONT_WIDTH_PX; ++glyph_x) {
                if ((row_bits & (1u << glyph_x)) == 0) continue;
                int pixel_x = character_x + glyph_x * scale;
                int pixel_y = y + glyph_y * scale;
                for (int scale_y = 0; scale_y < scale; ++scale_y) {
                    for (int scale_x = 0; scale_x < scale; ++scale_x) {
                        band_put_pixel(pixel_x + scale_x,
                                pixel_y + scale_y, color);
                    }
                }
            }
        }
    }
}

static int text_width(const char *text, int scale)
{
    return text == NULL ? 0 : (int)strlen(text) * FONT_WIDTH_PX * scale;
}

static void draw_text_right(int right_x, int y, const char *text, int scale,
        uint16_t color)
{
    draw_text(right_x - text_width(text, scale), y, text, scale, color);
}

static void log_draw_failure_throttled(const char *what, esp_err_t error)
{
    if (s_draw_error_count % DISPLAY_ERROR_LOG_PERIOD == 0) {
        ESP_LOGW(TAG, "%s failed: %s (occurrence %u)", what,
                esp_err_to_name(error), (unsigned)(s_draw_error_count + 1));
    }
    ++s_draw_error_count;
}

/* Send the current band. Caller must hold s_stripe_free; ownership passes to
 * DMA on success and returns from on_color_trans_done(). */
static bool stripe_present(void)
{
    esp_err_t error = esp_lcd_panel_draw_bitmap(s_panel,
            0, s_band_y, DISPLAY_WIDTH_PX, s_band_y + s_band_height, s_stripe);
    if (error != ESP_OK) {
        xSemaphoreGive(s_stripe_free); /* DMA never started */
        log_draw_failure_throttled("draw_bitmap", error);
        return false;
    }
    return true;
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
        /* HW_CONFIRMED: module exposes no CS pad (CS tied low on-board).
         * cs_gpio_num = -1 is the real esp_lcd/spi_master "no CS" path
         * (spics_io_num = -1). With CS permanently asserted the panel
         * needs SPI mode 3 (SCLK idle high) instead of mode 0, otherwise
         * the first clock edge after idle is misinterpreted. */
        .cs_gpio_num = BOARD_ST7789_CS_GPIO,
        .dc_gpio_num = BOARD_ST7789_DC_GPIO,
        .spi_mode = BOARD_ST7789_CS_GPIO < 0 ? 3 : 0,
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
    /* ST7789 datasheet reset timing: after RESX release (or SWRESET) the
     * panel needs up to 120 ms before Sleep Out is accepted. The IDF 5.3
     * driver only waits 10 ms (HW reset) / 20 ms (SWRESET), so settle here
     * before esp_lcd_panel_init() sends SLPOUT. */
    vTaskDelay(pdMS_TO_TICKS(120));
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
    for (int band_y = 0; band_y < DISPLAY_HEIGHT_PX;
            band_y += DISPLAY_BAND_HEIGHT_PX) {
        if (xSemaphoreTake(s_stripe_free, DISPLAY_DMA_WAIT_TICKS) != pdTRUE) {
            return init_failed("clear-screen stripe wait", ESP_ERR_TIMEOUT);
        }
        s_band_y = band_y;
        s_band_height = DISPLAY_BAND_HEIGHT_PX;
        stripe_fill(COLOR_BLACK);
        if (!stripe_present()) {
            return init_failed("clear-screen draw", ESP_FAIL);
        }
    }

    /* TODO_HW_CONFIRM: BLK assumed active-high; invert if the panel stays
     * dark with everything else working. */
    (void)gpio_set_level(BOARD_ST7789_BL_GPIO, 1);

    s_ready = true;
    ESP_LOGI(TAG, "ST7789 240x240 ready on SPI2 (MOSI=%d SCLK=%d DC=%d "
            "RST=%d BL=%d, no CS pad, SPI mode %d); themes=dark,light",
            BOARD_ST7789_MOSI_GPIO, BOARD_ST7789_SCLK_GPIO,
            BOARD_ST7789_DC_GPIO, BOARD_ST7789_RST_GPIO,
            BOARD_ST7789_BL_GPIO, BOARD_ST7789_CS_GPIO < 0 ? 3 : 0);
    return true;
}

typedef enum {
    DISPLAY_TIME_UNSYNCED = 0,
    DISPLAY_TIME_UTC,
    DISPLAY_TIME_LOCAL_DAY,
    DISPLAY_TIME_LOCAL_NIGHT,
} display_time_state_t;

typedef struct {
    char clock_value[12];
    char date_value[20];
    char period_label[8];
    char primary_label[24];
    char primary_value[16];
    char humidity_value[16];
    char humidity_meta[16];
    char pressure_value[16];
    char pressure_meta[16];
    char gas_value[16];
    char gas_meta[16];
    char mq7_value[16];
    char mq7_meta[16];
    char bme_footer[20];
    char mq7_footer[20];
    char network_header[12];
    char network_footer[16];
    display_time_state_t time_state;
    sensor_status_t primary_status;
    sensor_status_t bme_status;
    sensor_status_t gas_status;
    sensor_status_t mq7_status;
    bool wifi_connected;
    bool provisioned;
    bool ble_mesh_ready;
} display_frame_t;

static const char *status_short(sensor_status_t status)
{
    switch (status) {
    case SENSOR_STATUS_VALID: return "OK";
    case SENSOR_STATUS_NOT_INITIALIZED: return "OFF";
    case SENSOR_STATUS_TIMEOUT: return "TIMEOUT";
    case SENSOR_STATUS_CHECKSUM_ERROR: return "CHECKSUM";
    case SENSOR_STATUS_IO_ERROR: return "IO ERROR";
    case SENSOR_STATUS_OUT_OF_RANGE: return "RANGE";
    case SENSOR_STATUS_HEATER_WARMUP: return "WARMUP";
    case SENSOR_STATUS_CALIBRATION_MISSING: return "UNCAL";
    case SENSOR_STATUS_RAW_UNCALIBRATED: return "RAW";
    case SENSOR_STATUS_RATE_LIMITED: return "WAIT";
    case SENSOR_STATUS_NO_NEW_DATA: return "NO DATA";
    case SENSOR_STATUS_UNSUPPORTED: return "N/A";
    case SENSOR_STATUS_NOT_CONNECTED: return "N/C";
    default: return "ERROR";
    }
}

static uint16_t status_color(const display_palette_t *palette,
        sensor_status_t status)
{
    if (status == SENSOR_STATUS_VALID) return palette->ok;
    if (status == SENSOR_STATUS_CALIBRATION_MISSING ||
            status == SENSOR_STATUS_RAW_UNCALIBRATED ||
            status == SENSOR_STATUS_HEATER_WARMUP ||
            status == SENSOR_STATUS_RATE_LIMITED ||
            status == SENSOR_STATUS_NO_NEW_DATA) {
        return palette->warning;
    }
    return palette->error;
}

static void format_time(uint64_t epoch_ms, display_frame_t *frame)
{
    (void)snprintf(frame->clock_value, sizeof(frame->clock_value),
            "--:--:--");
    (void)snprintf(frame->date_value, sizeof(frame->date_value),
            "DATE UNSYNCED");
    frame->period_label[0] = '\0';
    frame->time_state = DISPLAY_TIME_UNSYNCED;
    if (epoch_ms == 0) return;

    time_t seconds = (time_t)(epoch_ms / 1000ULL);
    struct tm broken_down;
    const char *timezone = getenv("TZ");
    bool has_timezone = timezone != NULL && timezone[0] != '\0';
    struct tm *converted = has_timezone
            ? localtime_r(&seconds, &broken_down)
            : gmtime_r(&seconds, &broken_down);
    if (converted == NULL || broken_down.tm_year < 124) return;

    (void)snprintf(frame->clock_value, sizeof(frame->clock_value),
            "%02d:%02d:%02d", broken_down.tm_hour, broken_down.tm_min,
            broken_down.tm_sec);
    if (!has_timezone) {
        /* SNTP provides a real epoch, but this project has no configured
         * timezone source. Show truthful UTC and withhold DAY/NIGHT. */
        (void)snprintf(frame->date_value, sizeof(frame->date_value),
                "%02d/%02d/%04d UTC", broken_down.tm_mday,
                broken_down.tm_mon + 1, broken_down.tm_year + 1900);
        (void)snprintf(frame->period_label, sizeof(frame->period_label),
                "UTC");
        frame->time_state = DISPLAY_TIME_UTC;
        return;
    }

    (void)snprintf(frame->date_value, sizeof(frame->date_value),
            "%02d/%02d/%04d", broken_down.tm_mday,
            broken_down.tm_mon + 1, broken_down.tm_year + 1900);
    bool daytime = broken_down.tm_hour >= 6 && broken_down.tm_hour < 18;
    (void)snprintf(frame->period_label, sizeof(frame->period_label), "%s",
            daytime ? "DAY" : "NIGHT");
    frame->time_state = daytime
            ? DISPLAY_TIME_LOCAL_DAY : DISPLAY_TIME_LOCAL_NIGHT;
}

static void format_frame(const environment_raw_sensor_sample_t *sample,
        bool wifi_connected, bool provisioned, bool ble_mesh_ready,
        display_frame_t *frame)
{
    memset(frame, 0, sizeof(*frame));
    format_time(sample->observed_at_epoch_ms, frame);
    frame->bme_status = sample->bme680_status;
    frame->gas_status = sample->bme680_gas_status;
    frame->mq7_status = sample->mq7_status;
    frame->wifi_connected = wifi_connected;
    frame->provisioned = provisioned;
    frame->ble_mesh_ready = ble_mesh_ready;

    bool bme_valid = sample->bme680_status == SENSOR_STATUS_VALID &&
            isfinite(sample->bme680_temperature_degc) &&
            isfinite(sample->bme680_humidity_percent) &&
            isfinite(sample->pressure_hpa);

    if (bme_valid) {
        (void)snprintf(frame->primary_label, sizeof(frame->primary_label),
                "BME680 TEMP");
        (void)snprintf(frame->primary_value, sizeof(frame->primary_value),
                "%.1fC", sample->bme680_temperature_degc);
        (void)snprintf(frame->humidity_value, sizeof(frame->humidity_value),
                "%.0f%%", sample->bme680_humidity_percent);
        (void)snprintf(frame->humidity_meta, sizeof(frame->humidity_meta),
                "BME680");
        frame->primary_status = SENSOR_STATUS_VALID;
    } else {
        (void)snprintf(frame->primary_label, sizeof(frame->primary_label),
                "TEMP UNAVAILABLE");
        (void)snprintf(frame->primary_value, sizeof(frame->primary_value),
                "--.-C");
        (void)snprintf(frame->humidity_value, sizeof(frame->humidity_value),
                "--%%");
        (void)snprintf(frame->humidity_meta, sizeof(frame->humidity_meta),
                "NO DATA");
        frame->primary_status = sample->bme680_status;
    }

    if (bme_valid) {
        (void)snprintf(frame->pressure_value, sizeof(frame->pressure_value),
                "%.1f", sample->pressure_hpa);
        (void)snprintf(frame->pressure_meta, sizeof(frame->pressure_meta),
                "BME680");
    } else {
        (void)snprintf(frame->pressure_value, sizeof(frame->pressure_value),
                "---");
        (void)snprintf(frame->pressure_meta, sizeof(frame->pressure_meta),
                "%s", status_short(sample->bme680_status));
    }

    if (sample->bme680_gas_status == SENSOR_STATUS_VALID &&
            isfinite(sample->gas_resistance_ohm)) {
        (void)snprintf(frame->gas_value, sizeof(frame->gas_value), "%.0fK",
                sample->gas_resistance_ohm / 1000.0);
    } else {
        (void)snprintf(frame->gas_value, sizeof(frame->gas_value), "---");
    }
    (void)snprintf(frame->gas_meta, sizeof(frame->gas_meta), "%s",
            status_short(sample->bme680_gas_status));

    if (sample->mq7_status == SENSOR_STATUS_VALID &&
            isfinite(sample->mq7_co_ppm)) {
        (void)snprintf(frame->mq7_value, sizeof(frame->mq7_value), "%.1fPPM",
                sample->mq7_co_ppm);
    } else if (sample->mq7_status != SENSOR_STATUS_NOT_INITIALIZED &&
            sample->mq7_status != SENSOR_STATUS_IO_ERROR) {
        /* A zero raw input is still real evidence, so show it with its
         * truthful RANGE/UNCAL status instead of replacing it with CO ppm. */
        (void)snprintf(frame->mq7_value, sizeof(frame->mq7_value), "%dMV",
                sample->mq7_adc_millivolts);
    } else {
        (void)snprintf(frame->mq7_value, sizeof(frame->mq7_value), "---");
    }
    (void)snprintf(frame->mq7_meta, sizeof(frame->mq7_meta), "%s",
            status_short(sample->mq7_status));

    (void)snprintf(frame->bme_footer, sizeof(frame->bme_footer), "BME %s",
            status_short(sample->bme680_status));
    (void)snprintf(frame->mq7_footer, sizeof(frame->mq7_footer), "MQ7 %s",
            status_short(sample->mq7_status));
    (void)snprintf(frame->network_header, sizeof(frame->network_header), "%s",
            wifi_connected ? "WIFI" : (provisioned ? "OFFLINE" :
            (ble_mesh_ready ? "BLE" : "LOCAL")));
    (void)snprintf(frame->network_footer, sizeof(frame->network_footer), "%s",
            wifi_connected ? "LOCAL + WIFI" :
            (provisioned ? "LOCAL WIFI DOWN" :
            (ble_mesh_ready ? "LOCAL + BLE" : "LOCAL ONLY")));
}

static void draw_sun_icon(int center_x, int center_y, uint16_t color)
{
    fill_rect(center_x - 3, center_y - 3, 7, 7, color);
    fill_rect(center_x, center_y - 8, 1, 3, color);
    fill_rect(center_x, center_y + 6, 1, 3, color);
    fill_rect(center_x - 8, center_y, 3, 1, color);
    fill_rect(center_x + 6, center_y, 3, 1, color);
    band_put_pixel(center_x - 6, center_y - 6, color);
    band_put_pixel(center_x + 6, center_y - 6, color);
    band_put_pixel(center_x - 6, center_y + 6, color);
    band_put_pixel(center_x + 6, center_y + 6, color);
}

static void draw_moon_icon(int center_x, int center_y, uint16_t color)
{
    for (int y = -7; y <= 7; ++y) {
        for (int x = -7; x <= 7; ++x) {
            int outer_distance = x * x + y * y;
            int cutout_x = x + 4;
            int cutout_y = y - 2;
            int cutout_distance = cutout_x * cutout_x + cutout_y * cutout_y;
            if (outer_distance <= 49 && cutout_distance > 36) {
                band_put_pixel(center_x + x, center_y + y, color);
            }
        }
    }
}

static void draw_time_period(const display_frame_t *frame,
        const display_palette_t *palette)
{
    if (frame->time_state == DISPLAY_TIME_LOCAL_DAY) {
        draw_sun_icon(181, 32, palette->accent);
        draw_text(194, 28, frame->period_label, 1, palette->foreground);
    } else if (frame->time_state == DISPLAY_TIME_LOCAL_NIGHT) {
        draw_moon_icon(174, 32, palette->accent);
        draw_text(187, 28, frame->period_label, 1, palette->foreground);
    } else if (frame->time_state == DISPLAY_TIME_UTC) {
        draw_text_right(230, 28, frame->period_label, 1, palette->muted);
    }
}

static void draw_scene(const display_frame_t *frame,
        const display_palette_t *palette)
{
    fill_rect(0, 0, DISPLAY_WIDTH_PX, 3, palette->accent);
    draw_text(10, 7, "ENV NODE", 1, palette->foreground);
    uint16_t network_color = frame->wifi_connected ? palette->ok :
            (frame->provisioned ? palette->warning :
            (frame->ble_mesh_ready ? palette->ok : palette->accent));
    draw_text_right(230, 7, frame->network_header, 1, network_color);

    draw_text(10, 20, frame->clock_value, 2, palette->foreground);
    draw_text(10, 40, frame->date_value, 1,
            frame->time_state == DISPLAY_TIME_UNSYNCED
                    ? palette->warning : palette->muted);
    draw_time_period(frame, palette);

    fill_rect(10, 53, 220, 1, palette->divider);
    draw_text(12, 59, frame->primary_label, 1, palette->muted);
    draw_text_right(228, 59, status_short(frame->primary_status), 1,
            status_color(palette, frame->primary_status));
    int primary_scale = text_width(frame->primary_value, 3) <= 216 ? 3 : 2;
    draw_text(12, 72, frame->primary_value, primary_scale,
            palette->foreground);
    draw_text(14, 99, "REAL BME680 SAMPLE", 1, palette->muted);

    fill_rect(10, 110, 220, 1, palette->divider);
    fill_rect(120, 116, 1, 40, palette->divider);
    draw_text(12, 117, "HUMIDITY", 1, palette->muted);
    draw_text(130, 117, "PRESSURE HPA", 1, palette->muted);
    draw_text(12, 130, frame->humidity_value, 2, palette->foreground);
    draw_text(130, 130, frame->pressure_value, 2, palette->foreground);
    draw_text(12, 148, frame->humidity_meta, 1, palette->muted);
    draw_text(130, 148, frame->pressure_meta, 1, palette->muted);

    fill_rect(10, 159, 220, 1, palette->divider);
    fill_rect(120, 165, 1, 41, palette->divider);
    draw_text(12, 166, "BME GAS KOHM", 1, palette->muted);
    draw_text(130, 166, "MQ7 RAW / CO", 1, palette->muted);
    draw_text(12, 179, frame->gas_value, 2, palette->foreground);
    draw_text(130, 179, frame->mq7_value, 2, palette->foreground);
    draw_text(12, 198, frame->gas_meta, 1,
            status_color(palette, frame->gas_status));
    draw_text(130, 198, frame->mq7_meta, 1,
            status_color(palette, frame->mq7_status));

    fill_rect(10, 209, 220, 1, palette->divider);
    fill_rect(8, 218, 5, 5, status_color(palette, frame->bme_status));
    draw_text(17, 216, frame->bme_footer, 1, palette->foreground);
    fill_rect(124, 218, 5, 5, status_color(palette, frame->mq7_status));
    draw_text_right(232, 216, frame->mq7_footer, 1,
            status_color(palette, frame->mq7_status));
    draw_text(8, 229, frame->network_footer, 1, network_color);
    draw_text_right(232, 229, palette->name, 1, palette->muted);
}

static bool render_frame(const display_frame_t *frame,
        const display_palette_t *palette)
{
    for (int band_y = 0; band_y < DISPLAY_HEIGHT_PX;
            band_y += DISPLAY_BAND_HEIGHT_PX) {
        if (xSemaphoreTake(s_stripe_free, DISPLAY_DMA_WAIT_TICKS) != pdTRUE) {
            log_draw_failure_throttled("band wait", ESP_ERR_TIMEOUT);
            return false;
        }
        s_band_y = band_y;
        s_band_height = DISPLAY_BAND_HEIGHT_PX;
        stripe_fill(palette->background);
        draw_scene(frame, palette);
        if (!stripe_present()) return false;
    }
    return true;
}

void display_st7789_set_theme(display_st7789_theme_t theme)
{
    if (theme != DISPLAY_ST7789_THEME_DARK &&
            theme != DISPLAY_ST7789_THEME_LIGHT) return;
    if (theme != s_theme) {
        s_theme = theme;
        ESP_LOGI(TAG, "UI theme selected: %s",
                theme == DISPLAY_ST7789_THEME_DARK ? "dark" : "light");
    }
}

void display_st7789_render_sample(
        const environment_raw_sensor_sample_t *sample,
        bool wifi_connected,
        bool provisioned,
        bool ble_mesh_ready)
{
    if (!s_ready || sample == NULL) return;

    display_frame_t frame;
    format_frame(sample, wifi_connected, provisioned, ble_mesh_ready, &frame);
    const display_palette_t *palette = s_theme == DISPLAY_ST7789_THEME_LIGHT
            ? &LIGHT_PALETTE : &DARK_PALETTE;
    if (!render_frame(&frame, palette)) return;

    ++s_render_count;
    ESP_LOGI(TAG, "render=%u theme=%s time=%s date=\"%s\" period=%s "
            "primary=%s source=%s bme=%s gas=%s mq7=%s raw=%dmV network=%s",
            (unsigned)s_render_count, palette->name, frame.clock_value,
            frame.date_value,
            frame.period_label[0] != '\0' ? frame.period_label : "none",
            frame.primary_value, frame.primary_label,
            sensor_status_name(sample->bme680_status),
            sensor_status_name(sample->bme680_gas_status),
            sensor_status_name(sample->mq7_status), sample->mq7_adc_millivolts,
            frame.network_footer);
}
