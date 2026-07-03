#ifndef BME680_I2C_H
#define BME680_I2C_H

#include "driver/gpio.h"

typedef struct {
    double temperature;
    double humidity;
    double pressure;
    double gas_resistance;
} bme680_data_t;

void bme680_i2c_init(gpio_num_t sda_pin, gpio_num_t scl_pin);
int bme680_i2c_read(bme680_data_t *data);

#endif /* BME680_I2C_H */
