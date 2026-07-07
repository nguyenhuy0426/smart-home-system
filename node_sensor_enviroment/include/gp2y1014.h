#ifndef GP2Y1014_H
#define GP2Y1014_H

#include "adc_reader.h"
#include "gp2y1014_conversion.h"

#include "driver/gpio.h"

#include <stdbool.h>

bool gp2y1014_init(gpio_num_t led_pin,
                   adc_unit_t adc_unit,
                   adc_oneshot_unit_handle_t adc_handle,
                   adc_channel_t channel,
                   const gp2y1014_calibration_t *calibration);
sensor_status_t gp2y1014_read(gp2y1014_reading_t *reading);

#endif
