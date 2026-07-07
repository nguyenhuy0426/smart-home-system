#ifndef ADC_READER_H
#define ADC_READER_H

#include "sensor_status.h"

#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_oneshot.h"

#include <stdbool.h>
#include <stddef.h>

typedef struct {
    adc_oneshot_unit_handle_t unit_handle;
    adc_cali_handle_t calibration_handle;
    adc_channel_t channel;
    bool ready;
} adc_reader_t;

bool adc_reader_create_unit(adc_unit_t unit_id,
                            adc_oneshot_unit_handle_t *out_handle);
bool adc_reader_init(adc_reader_t *reader,
                     adc_unit_t unit_id,
                     adc_oneshot_unit_handle_t unit_handle,
                     adc_channel_t channel);
sensor_status_t adc_reader_read_mv(const adc_reader_t *reader,
                                   size_t sample_count,
                                   unsigned delay_between_samples_us,
                                   int *out_millivolts);

#endif
