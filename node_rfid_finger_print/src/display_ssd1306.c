#include "display_ssd1306.h"

#include "board_pins.h"

#include "driver/i2c_master.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_ssd1306.h"
#include "esp_lcd_panel_vendor.h"
#include "esp_log.h"

#include <string.h>

#define TAG "DISPLAY_SSD1306"

/* SSD1306 128x64 monochrome, 1 bit/pixel. The esp_lcd SSD1306 driver uses
 * horizontal addressing mode (verified in esp_lcd_panel_ssd1306.c init: memory
 * addressing mode 0x00), so the frame buffer is laid out as 8 vertical pages of
 * 128 columns; byte index = page*128 + x, bit (y & 7) is the pixel at (x, y),
 * bit 0 = top row of the page. */
#define OLED_WIDTH_PX  128
#define OLED_HEIGHT_PX 64
#define OLED_PAGES     (OLED_HEIGHT_PX / 8)
#define OLED_FB_BYTES  (OLED_WIDTH_PX * OLED_PAGES) /* 1024 */

/* 8x8 glyph cell; text grid is 16 columns x 8 rows at scale 1. */
#define FONT_W 8
#define FONT_H 8

/* Canonical SSD1306 I2C panel-IO control encoding (Espressif i2c_oled example):
 * one control byte with the D/C select bit at offset 6. */
#define OLED_CONTROL_PHASE_BYTES 1
#define OLED_DC_BIT_OFFSET       6

/*
 * 8x8 glyphs for ASCII 0x20..0x5F, from the public-domain font8x8_basic set
 * (https://github.com/dhepper/font8x8). Bit 0 of each row byte is the leftmost
 * pixel. Lowercase input is mapped to uppercase at lookup time; out-of-range
 * characters render as '?'. Same table as the environment node's ST7789 module.
 */
static const uint8_t s_font8x8[0x60 - 0x20][FONT_H] = {
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
static i2c_master_bus_handle_t s_i2c_bus;
static esp_lcd_panel_io_handle_t s_panel_io;
static esp_lcd_panel_handle_t s_panel;

/* Working framebuffer being composed, and a shadow of what is currently on the
 * panel. A screen is pushed over I2C only when the two differ, so a static idle
 * screen redrawn every poll iteration costs a single memcmp and no I2C traffic. */
static uint8_t s_framebuffer[OLED_FB_BYTES];
static uint8_t s_shadow[OLED_FB_BYTES];
static bool s_shadow_valid;

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

static void fb_set_pixel(int x, int y)
{
    if (x < 0 || x >= OLED_WIDTH_PX || y < 0 || y >= OLED_HEIGHT_PX) return;
    s_framebuffer[(y / 8) * OLED_WIDTH_PX + x] |= (uint8_t)(1u << (y & 7));
}

/* Draw one glyph at pixel (x, y) with an integer scale factor. */
static void fb_draw_char(int x, int y, char character, int scale)
{
    const uint8_t *glyph = glyph_for(character);
    for (int glyph_y = 0; glyph_y < FONT_H; ++glyph_y) {
        uint8_t row_bits = glyph[glyph_y];
        if (row_bits == 0) continue;
        for (int glyph_x = 0; glyph_x < FONT_W; ++glyph_x) {
            if ((row_bits & (1u << glyph_x)) == 0) continue;
            for (int sy = 0; sy < scale; ++sy) {
                for (int sx = 0; sx < scale; ++sx) {
                    fb_set_pixel(x + glyph_x * scale + sx, y + glyph_y * scale + sy);
                }
            }
        }
    }
}

static void fb_draw_text(int x, int y, const char *text, int scale)
{
    for (int i = 0; text[i] != '\0'; ++i) {
        fb_draw_char(x + i * FONT_W * scale, y, text[i], scale);
    }
}

/* Horizontally center a scale-1 or scale-2 string on a given pixel row. */
static void fb_draw_text_centered(int y, const char *text, int scale)
{
    int width = (int)strlen(text) * FONT_W * scale;
    int x = (OLED_WIDTH_PX - width) / 2;
    if (x < 0) x = 0;
    fb_draw_text(x, y, text, scale);
}

static void fb_clear(void)
{
    memset(s_framebuffer, 0, sizeof(s_framebuffer));
}

/* Push the composed framebuffer to the panel only if it changed. Never blocks
 * the caller beyond the esp_lcd I2C driver's own bounded transfer, and any
 * failure is logged once per state change without affecting the access path. */
static void fb_commit(void)
{
    if (!s_ready) return;
    if (s_shadow_valid && memcmp(s_shadow, s_framebuffer, sizeof(s_framebuffer)) == 0) {
        return;
    }
    esp_err_t error = esp_lcd_panel_draw_bitmap(s_panel, 0, 0,
            OLED_WIDTH_PX, OLED_HEIGHT_PX, s_framebuffer);
    if (error != ESP_OK) {
        ESP_LOGW(TAG, "draw_bitmap failed: %s; display left as-is",
                esp_err_to_name(error));
        return;
    }
    memcpy(s_shadow, s_framebuffer, sizeof(s_framebuffer));
    s_shadow_valid = true;
}

/* Compose a standard status screen: a large (scale-2) title on the top half and
 * up to two small (scale-1) detail lines below, then commit. */
static void render_screen(const char *title, const char *line_a, const char *line_b)
{
    fb_clear();
    fb_draw_text_centered(4, title, 2);        /* rows 0..1 (16 px) */
    if (line_a != NULL) fb_draw_text_centered(34, line_a, 1); /* page 4 */
    if (line_b != NULL) fb_draw_text_centered(48, line_b, 1); /* page 6 */
    fb_commit();
}

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
    if (s_i2c_bus != NULL) {
        (void)i2c_del_master_bus(s_i2c_bus);
        s_i2c_bus = NULL;
    }
}

static bool init_failed(const char *step, esp_err_t error)
{
    ESP_LOGE(TAG, "%s failed: %s; OLED disabled (access-control path "
            "unaffected)", step, esp_err_to_name(error));
    display_teardown();
    return false;
}

bool display_ssd1306_init(void)
{
    if (s_init_attempted) {
        ESP_LOGW(TAG, "init called more than once; ignoring "
                "(I2C bus must not be re-initialized)");
        return s_ready;
    }
    s_init_attempted = true;

    i2c_master_bus_config_t bus_config = {
        .i2c_port = BOARD_OLED_I2C_PORT,
        .sda_io_num = BOARD_OLED_SDA_GPIO,
        .scl_io_num = BOARD_OLED_SCL_GPIO,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    esp_err_t error = i2c_new_master_bus(&bus_config, &s_i2c_bus);
    if (error != ESP_OK) return init_failed("i2c_new_master_bus", error);

    esp_lcd_panel_io_i2c_config_t io_config = {
        .dev_addr = BOARD_OLED_I2C_ADDR,
        .scl_speed_hz = BOARD_OLED_I2C_SCL_HZ,
        .control_phase_bytes = OLED_CONTROL_PHASE_BYTES,
        .dc_bit_offset = OLED_DC_BIT_OFFSET,
        .lcd_cmd_bits = 8,
        .lcd_param_bits = 8,
    };
    error = esp_lcd_new_panel_io_i2c(s_i2c_bus, &io_config, &s_panel_io);
    if (error != ESP_OK) return init_failed("esp_lcd_new_panel_io_i2c", error);

    esp_lcd_panel_ssd1306_config_t ssd1306_config = {
        .height = BOARD_OLED_HEIGHT_PX,
    };
    esp_lcd_panel_dev_config_t panel_config = {
        .reset_gpio_num = -1, /* 4-pin I2C module has no reset line */
        .bits_per_pixel = 1,
        .vendor_config = &ssd1306_config,
    };
    error = esp_lcd_new_panel_ssd1306(s_panel_io, &panel_config, &s_panel);
    if (error != ESP_OK) return init_failed("esp_lcd_new_panel_ssd1306", error);

    error = esp_lcd_panel_reset(s_panel);
    if (error != ESP_OK) return init_failed("panel reset", error);
    error = esp_lcd_panel_init(s_panel);
    if (error != ESP_OK) return init_failed("panel init", error);
    error = esp_lcd_panel_disp_on_off(s_panel, true);
    if (error != ESP_OK) return init_failed("disp_on_off", error);

    s_ready = true;

    /* Clear to blank so the first show_* draws a fresh screen. */
    fb_clear();
    if (esp_lcd_panel_draw_bitmap(s_panel, 0, 0, OLED_WIDTH_PX, OLED_HEIGHT_PX,
            s_framebuffer) == ESP_OK) {
        memcpy(s_shadow, s_framebuffer, sizeof(s_framebuffer));
        s_shadow_valid = true;
    }

    ESP_LOGI(TAG, "SSD1306 128x%d ready on I2C (SDA=%d SCL=%d addr=0x%02X)",
            BOARD_OLED_HEIGHT_PX, BOARD_OLED_SDA_GPIO, BOARD_OLED_SCL_GPIO,
            BOARD_OLED_I2C_ADDR);
    return true;
}

void display_ssd1306_show_boot(void)
{
    if (!s_ready) return;
    render_screen("ACCESS", "BOOTING", NULL);
}

void display_ssd1306_show_provisioning(void)
{
    if (!s_ready) return;
    render_screen("SETUP", "BLE MESH PROV", "NO READER YET");
}

void display_ssd1306_show_no_reader(void)
{
    if (!s_ready) return;
    render_screen("NO READER", "LOCKED", "CHECK WIRING");
}

void display_ssd1306_show_ready(bool rfid_ready, bool fingerprint_ready,
                                bool wifi_connected)
{
    if (!s_ready) return;
    char readers[24];
    (void)snprintf(readers, sizeof(readers), "RFID:%s FP:%s",
            rfid_ready ? "OK" : "--", fingerprint_ready ? "OK" : "--");
    render_screen("READY", readers, wifi_connected ? "WIFI:UP" : "WIFI:DOWN");
}

void display_ssd1306_show_enroll_open(void)
{
    if (!s_ready) return;
    render_screen("ENROLL", "PRESENT CARD", NULL);
}

void display_ssd1306_show_enroll_result(bool succeeded, bool already_enrolled)
{
    if (!s_ready) return;
    if (!succeeded) {
        render_screen("ENROLL", "FAILED", NULL);
    } else if (already_enrolled) {
        render_screen("ENROLL", "KNOWN CARD", NULL);
    } else {
        render_screen("ENROLL", "CARD ADDED", NULL);
    }
}

void display_ssd1306_show_result(access_credential_kind_t kind,
                                 access_result_t result)
{
    if (!s_ready) return;
    const char *kind_text =
            kind == ACCESS_CREDENTIAL_RFID ? "RFID" : "FINGERPRINT";
    switch (result) {
    case ACCESS_RESULT_GRANTED:
        render_screen("GRANTED", kind_text, "UNLOCKED");
        break;
    case ACCESS_RESULT_SENSOR_ERROR:
        render_screen("ERROR", kind_text, "LOCKED");
        break;
    case ACCESS_RESULT_DENIED:
    default:
        render_screen("DENIED", kind_text, "LOCKED");
        break;
    }
}
