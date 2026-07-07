#ifndef BME680_I2C_H
#define BME680_I2C_H

#include "sensor_status.h"

#include "driver/gpio.h"

#include <stdbool.h>

typedef struct {
    sensor_status_t status;
    sensor_status_t gas_status;
    double temperature_degc;
    double humidity_percent;
    double pressure_hpa;
    double gas_resistance_ohm;
} bme680_data_t;

bool bme680_i2c_init(gpio_num_t sda_pin, gpio_num_t scl_pin);
sensor_status_t bme680_i2c_read(bme680_data_t *data);

#endif
