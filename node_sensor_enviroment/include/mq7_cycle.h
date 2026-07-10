#ifndef MQ7_CYCLE_H
#define MQ7_CYCLE_H

#include <stdbool.h>
#include <stdint.h>

#define MQ7_HIGH_HEAT_MS UINT64_C(60000)
#define MQ7_LOW_HEAT_MS UINT64_C(90000)
#define MQ7_SAMPLE_WINDOW_MS UINT64_C(10000)

typedef enum {
    MQ7_PHASE_HIGH_HEAT = 0,
    MQ7_PHASE_LOW_HEAT,
    MQ7_PHASE_SAMPLE
} mq7_heater_phase_t;

mq7_heater_phase_t mq7_cycle_phase(uint64_t elapsed_ms);
const char *mq7_heater_phase_name(mq7_heater_phase_t phase);

#endif
