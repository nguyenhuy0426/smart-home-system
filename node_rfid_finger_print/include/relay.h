#ifndef RELAY_H
#define RELAY_H

#include <stdbool.h>
#include <stdint.h>

bool relay_init(int gpio_pin, int active_level, uint32_t unlock_pulse_ms);
bool relay_trigger_unlock(void);
void relay_force_off(void);
bool relay_is_unlocked(void);

#endif
