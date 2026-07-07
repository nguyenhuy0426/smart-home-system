#ifndef MQ7_H
#define MQ7_H

#include "adc_reader.h"
#include "mq7_conversion.h"

#include "driver/gpio.h"

#include <stdbool.h>

bool mq7_init(adc_unit_t adc_unit,
              adc_oneshot_unit_handle_t adc_handle,
              adc_channel_t channel,
              gpio_num_t heater_control_pin,
              const mq7_calibration_t *calibration);
sensor_status_t mq7_read(mq7_reading_t *reading);
void mq7_heater_off(void);

#endif
