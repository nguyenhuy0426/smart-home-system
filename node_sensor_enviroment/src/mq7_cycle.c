#include "mq7_cycle.h"

mq7_heater_phase_t mq7_cycle_phase(uint64_t elapsed_ms)
{
    const uint64_t cycle_ms = MQ7_HIGH_HEAT_MS + MQ7_LOW_HEAT_MS;
    uint64_t position_ms = elapsed_ms % cycle_ms;
    if (position_ms < MQ7_HIGH_HEAT_MS) return MQ7_PHASE_HIGH_HEAT;
    if (position_ms < cycle_ms - MQ7_SAMPLE_WINDOW_MS) return MQ7_PHASE_LOW_HEAT;
    return MQ7_PHASE_SAMPLE;
}

bool mq7_cycle_uses_high_voltage(mq7_heater_phase_t phase)
{
    return phase == MQ7_PHASE_HIGH_HEAT;
}

const char *mq7_heater_phase_name(mq7_heater_phase_t phase)
{
    switch (phase) {
    case MQ7_PHASE_HIGH_HEAT: return "high_heat";
    case MQ7_PHASE_LOW_HEAT: return "low_heat";
    case MQ7_PHASE_SAMPLE: return "sample";
    default: return "invalid";
    }
}
