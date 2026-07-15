#include "adc_sample_math.h"
#include "bme680_status.h"
#include "bme68x_defs.h"
#include "environment_sensor_pipeline.h"
#include "gp2y1014_conversion.h"
#include "mq7_conversion.h"
#include "mq7_cycle.h"
#include "provisioning_parser.h"
#include "sensor_status.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

static void test_invalid_adc_and_uncalibrated_mq7(void)
{
    int average = 0;
    const int invalid_samples[] = {1024, -1, 2048};
    assert(adc_samples_average(invalid_samples, 3, &average) ==
            SENSOR_STATUS_OUT_OF_RANGE);
    const int overflow_samples[] = {1024, 4096};
    assert(adc_samples_average(overflow_samples, 2, &average) ==
            SENSOR_STATUS_OUT_OF_RANGE);

    mq7_reading_t reading = {0};
    mq7_calibration_t missing = {0};
    assert(mq7_convert_adc(1200, &missing, &reading) ==
            SENSOR_STATUS_CALIBRATION_MISSING);
    assert(reading.adc_millivolts == 1200);
    assert(isnan(reading.co_ppm));
    /* A real zero-millivolt acquisition is still raw evidence. Without a
     * calibration record, conversion truthfully reports calibration_missing
     * rather than implying a calibrated CO range decision. */
    assert(mq7_convert_adc(0, &missing, &reading) ==
            SENSOR_STATUS_CALIBRATION_MISSING);
    assert(reading.adc_millivolts == 0);
    assert(isnan(reading.co_ppm));

    mq7_calibration_t present = {
        .valid = true,
        .sensor_supply_mv = 5000.0,
        .adc_divider_ratio = 2.0,
        .load_resistor_ohm = 10000.0,
        .clean_air_resistance_ohm = 10000.0,
        .curve_a = 100.0,
        .curve_b = -1.0,
    };
    assert(mq7_convert_adc(0, &present, &reading) ==
            SENSOR_STATUS_OUT_OF_RANGE);
}

static void test_gp2y_invalid_sample(void)
{
    gp2y1014_calibration_t calibration = {
        .valid = true,
        .adc_divider_ratio = 2.0,
        .clean_air_voltage_mv = 900.0,
        .sensitivity_mv_per_ug_m3 = 5.0,
    };
    gp2y1014_reading_t reading = {0};
    assert(gp2y1014_convert_adc(-1, &calibration, &reading) ==
            SENSOR_STATUS_OUT_OF_RANGE);
    assert(isnan(reading.pm25_ug_m3));
    assert(gp2y1014_convert_adc(3200, &calibration, &reading) ==
            SENSOR_STATUS_OUT_OF_RANGE);
}

static void test_mq7_heater_cycle(void)
{
    assert(mq7_cycle_phase(0) == MQ7_PHASE_HIGH_HEAT);
    assert(mq7_cycle_phase(MQ7_HIGH_HEAT_MS - 1) == MQ7_PHASE_HIGH_HEAT);
    assert(mq7_cycle_phase(MQ7_HIGH_HEAT_MS) == MQ7_PHASE_LOW_HEAT);
    assert(mq7_cycle_phase(MQ7_HIGH_HEAT_MS + MQ7_LOW_HEAT_MS -
            MQ7_SAMPLE_WINDOW_MS) == MQ7_PHASE_SAMPLE);
    assert(mq7_cycle_phase(MQ7_HIGH_HEAT_MS + MQ7_LOW_HEAT_MS) ==
            MQ7_PHASE_HIGH_HEAT);
}

static void test_bme680_invalid_status(void)
{
    sensor_status_t gas_status;
    assert(bme680_validate_frame_status(BME68X_W_NO_NEW_DATA, 0, 0,
            &gas_status) == SENSOR_STATUS_NO_NEW_DATA);
    assert(gas_status == SENSOR_STATUS_NO_NEW_DATA);
    assert(bme680_validate_frame_status(BME68X_OK, 1,
            BME68X_NEW_DATA_MSK, &gas_status) == SENSOR_STATUS_VALID);
    assert(gas_status == SENSOR_STATUS_NO_NEW_DATA);
    assert(bme680_validate_frame_status(BME68X_OK, 1,
            BME68X_NEW_DATA_MSK | BME68X_GASM_VALID_MSK,
            &gas_status) == SENSOR_STATUS_VALID);
    assert(gas_status == SENSOR_STATUS_HEATER_WARMUP);
    assert(bme680_validate_frame_status(-3, 1,
            BME68X_NEW_DATA_MSK, &gas_status) == SENSOR_STATUS_IO_ERROR);
}

static void test_bounded_provisioning_parser(void)
{
    static const uint8_t valid[] =
            "HomeWiFi\0" "password8\0" "192.168.1.2\0"
            "env-01\0" "living-room\0"
            "0123456789abcdef0123456789abcdef\0";
    app_config_t configuration;
    assert(provisioning_parse_config(valid, sizeof(valid) - 1, &configuration));
    assert(strcmp(configuration.node_id, "env-01") == 0);
    assert(strcmp(configuration.room_id, "living-room") == 0);
    assert(strcmp(configuration.auth_key,
            "0123456789abcdef0123456789abcdef") == 0);

    assert(!provisioning_parse_config(valid, sizeof(valid) - 2, &configuration));
    uint8_t trailing[sizeof(valid)];
    memcpy(trailing, valid, sizeof(valid) - 1);
    trailing[sizeof(valid) - 1] = 'x';
    assert(!provisioning_parse_config(trailing, sizeof(trailing), &configuration));

    uint8_t control[] =
            "Bad\x01SSID\0" "password8\0" "192.168.1.2\0"
            "env-01\0" "room\0";
    assert(!provisioning_parse_config(control, sizeof(control) - 1, &configuration));
    uint8_t invalid_utf8[] = {
        0xC0, 0xAF, 0, 'p','a','s','s','w','o','r','d','8',0,
        '1','9','2','.','1','6','8','.','1','.','2',0,
        'e','n','v',0,'r','o','o','m',0
    };
    assert(!provisioning_parse_config(invalid_utf8,
            sizeof(invalid_utf8), &configuration));

    uint8_t oversized[96];
    memset(oversized, 'A', sizeof(oversized));
    size_t offset = 33;
    oversized[offset++] = 0;
    const char suffix[] = "password8\0" "192.168.1.2\0" "env\0" "room\0";
    memcpy(oversized + offset, suffix, sizeof(suffix) - 1);
    offset += sizeof(suffix) - 1;
    assert(!provisioning_parse_config(oversized, offset, &configuration));
}

static void test_invalid_metrics_are_not_numbers(void)
{
    environment_raw_sensor_sample_t sample = {
        .bme680_status = SENSOR_STATUS_NO_NEW_DATA,
        .bme680_gas_status = SENSOR_STATUS_HEATER_WARMUP,
        .bme680_temperature_degc = 25.1,
        .bme680_humidity_percent = 55.4,
        .pressure_hpa = 1013.25,
        .gas_resistance_ohm = 150000.0,
        .mq7_status = SENSOR_STATUS_CALIBRATION_MISSING,
        .mq7_phase = MQ7_PHASE_SAMPLE,
        .mq7_co_ppm = 1.8,
        .mq7_adc_millivolts = 1200,
        .gp2y_status = SENSOR_STATUS_IO_ERROR,
        .pm25_ug_m3 = 14.5,
        .sequence = 42,
        .observed_at_uptime_ms = 123456,
    };
    char json[3072];
    assert(environment_sensor_pipeline_build_reading(
            "env-01", "living-room", &sample,
            json, sizeof(json)));
    assert(strstr(json, "\"schemaVersion\":1") != NULL);
    assert(strstr(json, "\"observedAtEpochMs\":null") != NULL);
    assert(strstr(json, "\"observedAtUptimeMs\":123456") != NULL);
    assert(strstr(json,
            "\"ambientTemperature\":{\"status\":\"no_new_data\",\"value\"") == NULL);
    assert(strstr(json,
            "\"co\":{\"status\":\"calibration_missing\",\"value\"") == NULL);
    assert(strstr(json,
            "\"pm25\":{\"status\":\"io_error\",\"value\"") == NULL);
    assert(strstr(json,
            "\"coRawAdcMillivolts\":{\"status\":\"raw_uncalibrated\",\"value\":1200.000") != NULL);
    assert(strstr(json,
            "\"eco2\":{\"status\":\"unsupported\"") != NULL);
    assert(strstr(json, "\"dht22\":\"unsupported\"") != NULL);
    assert(strstr(json,
            "\"fusionStatus\":\"unsupported_dht22_removed\"") != NULL);
}

static void test_bme680_is_the_real_ambient_source(void)
{
    environment_raw_sensor_sample_t sample = {
        .bme680_status = SENSOR_STATUS_VALID,
        .bme680_gas_status = SENSOR_STATUS_VALID,
        .bme680_temperature_degc = 31.75,
        .bme680_humidity_percent = 69.25,
        .pressure_hpa = 1009.8,
        .gas_resistance_ohm = 1430000.0,
        .mq7_status = SENSOR_STATUS_CALIBRATION_MISSING,
        .mq7_phase = MQ7_PHASE_LOW_HEAT,
        .mq7_co_ppm = NAN,
        .mq7_adc_millivolts = 524,
        .gp2y_status = SENSOR_STATUS_NOT_CONNECTED,
        .pm25_ug_m3 = NAN,
        .sequence = 43,
        .observed_at_epoch_ms = 1720962000000ULL,
        .observed_at_uptime_ms = 128456,
    };
    char json[3072];
    assert(environment_sensor_pipeline_build_reading(
            "env-01", "living-room", &sample, json, sizeof(json)));
    assert(strstr(json,
            "\"ambientTemperature\":{\"status\":\"valid\",\"value\":31.750,"
            "\"unit\":\"degC\",\"source\":\"BME680\"}") != NULL);
    assert(strstr(json,
            "\"relativeHumidity\":{\"status\":\"valid\",\"value\":69.250,"
            "\"unit\":\"percent_rh\",\"source\":\"BME680\"}") != NULL);
    assert(strstr(json, "\"dht22\":\"unsupported\"") != NULL);
    assert(strstr(json, "\"source\":\"DHT22\"") == NULL);
}

int main(void)
{
    test_invalid_adc_and_uncalibrated_mq7();
    test_gp2y_invalid_sample();
    test_mq7_heater_cycle();
    test_bme680_invalid_status();
    test_bounded_provisioning_parser();
    test_invalid_metrics_are_not_numbers();
    test_bme680_is_the_real_ambient_source();
    puts("environment_safety_tests: all tests passed");
    return 0;
}
