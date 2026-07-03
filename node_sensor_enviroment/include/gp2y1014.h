#ifndef GP2Y1014_H
#define GP2Y1014_H

#include "driver/gpio.h"
#include "esp_adc/adc_oneshot.h"

void gp2y1014_init(gpio_num_t led_pin, adc_unit_t adc_unit, adc_channel_t channel);
double gp2y1014_read_pm25(void);

#endif /* GP2Y1014_H */
