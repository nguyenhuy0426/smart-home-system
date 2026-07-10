#ifndef MQ7_H
#define MQ7_H

#include "adc_reader.h"
#include "mq7_conversion.h"

#include <stdbool.h>

/*
 * The wired MQ-7 breakout exposes only VCC/GND/AO: its heater is powered
 * directly from the 5 V supply and cannot be duty-cycled by the MCU, so this
 * driver performs analog (ADC1) acquisition and conversion only. No heater
 * GPIO/PWM is initialized or controlled here.
 */
bool mq7_init(adc_unit_t adc_unit,
              adc_oneshot_unit_handle_t adc_handle,
              adc_channel_t channel,
              const mq7_calibration_t *calibration);
sensor_status_t mq7_read(mq7_reading_t *reading);

#endif
