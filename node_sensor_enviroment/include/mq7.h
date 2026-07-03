#ifndef MQ7_H
#define MQ7_H

#include "esp_adc/adc_oneshot.h"

void mq7_init(adc_unit_t adc_unit, adc_channel_t channel);
double mq7_read_co_ppm(void);

#endif /* MQ7_H */
