#ifndef DHT22_H
#define DHT22_H

#include "dht22_protocol.h"

#include "driver/gpio.h"
#include <stdbool.h>

bool dht22_init(gpio_num_t pin);
sensor_status_t dht22_read(double *temperature, double *humidity);

#endif /* DHT22_H */
