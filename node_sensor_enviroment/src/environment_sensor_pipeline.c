#include "environment_sensor_pipeline.h"

#include <inttypes.h>
#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

typedef struct {
    char *buffer;
    size_t capacity;
    size_t length;
    bool failed;
} json_writer_t;

static void append(json_writer_t *writer, const char *format, ...)
{
    if (writer == NULL || writer->failed || writer->length >= writer->capacity) return;
    va_list arguments;
    va_start(arguments, format);
    int written = vsnprintf(writer->buffer + writer->length,
            writer->capacity - writer->length, format, arguments);
    va_end(arguments);
    if (written < 0 || (size_t)written >= writer->capacity - writer->length) {
        writer->failed = true;
        return;
    }
    writer->length += (size_t)written;
}

static bool valid_identifier(const char *value)
{
    if (value == NULL || value[0] == '\0') return false;
    size_t length = 0;
    while (value[length] != '\0' && length < 64) {
        char character = value[length++];
        if (!((character >= 'a' && character <= 'z') ||
                (character >= 'A' && character <= 'Z') ||
                (character >= '0' && character <= '9') ||
                character == '_' || character == '-')) {
            return false;
        }
    }
    return length > 0 && length < 64 && value[length] == '\0';
}

static void append_metric(json_writer_t *writer,
                          bool *first,
                          const char *key,
                          sensor_status_t status,
                          double value,
                          const char *unit,
                          const char *source)
{
    sensor_status_t effective_status = status;
    if ((effective_status == SENSOR_STATUS_VALID ||
            effective_status == SENSOR_STATUS_RAW_UNCALIBRATED) &&
            !isfinite(value)) {
        effective_status = SENSOR_STATUS_OUT_OF_RANGE;
    }
    append(writer, "%s\"%s\":{\"status\":\"%s\"",
            *first ? "" : ",", key, sensor_status_name(effective_status));
    *first = false;
    if (effective_status == SENSOR_STATUS_VALID ||
            effective_status == SENSOR_STATUS_RAW_UNCALIBRATED) {
        append(writer, ",\"value\":%.3f", value);
    }
    append(writer, ",\"unit\":\"%s\",\"source\":\"%s\"}", unit, source);
}

int environment_sensor_pipeline_build_reading(
        const char *node_id,
        const char *room_id,
        const environment_raw_sensor_sample_t *sample,
        char *out_json,
        size_t out_json_size)
{
    if (!valid_identifier(node_id) || !valid_identifier(room_id) ||
            sample == NULL || out_json == NULL ||
            out_json_size < 256) {
        return 0;
    }
    out_json[0] = '\0';
    json_writer_t writer = {
        .buffer = out_json,
        .capacity = out_json_size,
    };
    append(&writer,
            "{\"schemaVersion\":%d,\"readingId\":\"%s_%08" PRIu64
            "\",\"nodeId\":\"%s\",\"roomId\":\"%s\","
            "\"sequence\":%" PRIu64 ",",
            ENVIRONMENT_READING_SCHEMA_VERSION, node_id, sample->sequence,
            node_id, room_id, sample->sequence);
    if (sample->observed_at_epoch_ms > 0) {
        append(&writer, "\"observedAtEpochMs\":%" PRIu64 ",",
                sample->observed_at_epoch_ms);
    } else {
        /* Clock never synced; the gateway supplies the fallback time. */
        append(&writer, "\"observedAtEpochMs\":null,");
    }
    append(&writer,
            "\"observedAtUptimeMs\":%" PRIu64 ",\"metrics\":{",
            sample->observed_at_uptime_ms);

    bool first = true;
    /* Keep the stable ambient metric keys, but source their real values from
     * the only installed temperature/humidity sensor. */
    append_metric(&writer, &first, "ambientTemperature", sample->bme680_status,
            sample->bme680_temperature_degc, "degC", "BME680");
    append_metric(&writer, &first, "relativeHumidity", sample->bme680_status,
            sample->bme680_humidity_percent, "percent_rh", "BME680");
    append_metric(&writer, &first, "bme680Temperature", sample->bme680_status,
            sample->bme680_temperature_degc, "degC", "BME680");
    append_metric(&writer, &first, "bme680Humidity", sample->bme680_status,
            sample->bme680_humidity_percent, "percent_rh", "BME680");
    append_metric(&writer, &first, "pressure", sample->bme680_status,
            sample->pressure_hpa, "hPa", "BME680");
    append_metric(&writer, &first, "gasResistance", sample->bme680_gas_status,
            sample->gas_resistance_ohm, "ohm", "BME680");
    append_metric(&writer, &first, "co", sample->mq7_status,
            sample->mq7_co_ppm, "ppm", "MQ7");
    append_metric(&writer, &first, "pm25", sample->gp2y_status,
            sample->pm25_ug_m3, "ug_m3", "GP2Y1014");
    append_metric(&writer, &first, "eco2", SENSOR_STATUS_UNSUPPORTED,
            NAN, "ppm", "BME680");
    append_metric(&writer, &first, "tvoc", SENSOR_STATUS_UNSUPPORTED,
            NAN, "ppb", "BME680");
    if (sample->mq7_adc_millivolts > 0) {
        append_metric(&writer, &first, "coRawAdcMillivolts",
                sample->mq7_status == SENSOR_STATUS_VALID
                        ? SENSOR_STATUS_VALID : SENSOR_STATUS_RAW_UNCALIBRATED,
                sample->mq7_adc_millivolts, "mV", "MQ7_ADC_RAW");
    }
    if (sample->gp2y_adc_millivolts > 0) {
        append_metric(&writer, &first, "pm25RawAdcMillivolts",
                sample->gp2y_status == SENSOR_STATUS_VALID
                        ? SENSOR_STATUS_VALID : SENSOR_STATUS_RAW_UNCALIBRATED,
                sample->gp2y_adc_millivolts, "mV", "GP2Y1014_ADC_RAW");
    }
    append(&writer, "},\"sensorStatus\":{"
            "\"dht22\":\"%s\",\"mq7\":\"%s\","
            "\"mq7HeaterPhase\":\"%s\",\"gp2y1014\":\"%s\","
            "\"bme680\":\"%s\",\"bme680Gas\":\"%s\"},"
            "\"diagnostics\":{",
            /* Schema-v1 compatibility only: DHT22 is permanently removed,
             * so status is truthful and no DHT value is emitted. */
            sensor_status_name(SENSOR_STATUS_UNSUPPORTED),
            sensor_status_name(sample->mq7_status),
            mq7_heater_phase_name(sample->mq7_phase),
            sensor_status_name(sample->gp2y_status),
            sensor_status_name(sample->bme680_status),
            sensor_status_name(sample->bme680_gas_status));

    append(&writer, "\"fusionStatus\":\"unsupported_dht22_removed\"");
    append(&writer, "}}");
    return !writer.failed && writer.length > 0;
}
