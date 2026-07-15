#include "environment_capability_descriptor.h"

static const char DESCRIPTOR[] =
    "{"
    "\"schemaVersion\":1,"
    "\"nodeType\":\"environment.sensor\","
    "\"displayName\":\"Environment Sensor Node\","
    "\"firmware\":{\"version\":\"1.0.0\",\"minGatewayDescriptorVersion\":1},"
    "\"transports\":{"
      "\"bleMesh\":{\"companyId\":741,\"vendorModelId\":1,\"supportsProvisioning\":true},"
      "\"wifi\":{\"requiredFor\":[\"telemetry\"]}},"
    "\"metrics\":["
      "{\"key\":\"ambientTemperature\",\"unit\":\"degC\",\"source\":\"BME680\"},"
      "{\"key\":\"relativeHumidity\",\"unit\":\"percent_rh\",\"source\":\"BME680\"},"
      "{\"key\":\"co\",\"unit\":\"ppm\",\"source\":\"MQ7\",\"requiresCalibration\":true},"
      "{\"key\":\"coRawAdcMillivolts\",\"unit\":\"mV\",\"source\":\"MQ7_ADC_RAW\"},"
      "{\"key\":\"bme680Temperature\",\"unit\":\"degC\",\"source\":\"BME680\"},"
      "{\"key\":\"bme680Humidity\",\"unit\":\"percent_rh\",\"source\":\"BME680\"},"
      "{\"key\":\"pressure\",\"unit\":\"hPa\",\"source\":\"BME680\"},"
      "{\"key\":\"gasResistance\",\"unit\":\"ohm\",\"source\":\"BME680\"}"
    "],"
    "\"unsupportedMetrics\":[\"eco2\",\"tvoc\"],"
    "\"unsupportedSensors\":[\"DHT22\",\"GP2Y1014\"],"
    "\"events\":[{\"key\":\"sensor.status\",\"severity\":\"warning\"}],"
    "\"actions\":[]"
    "}";

const char *environment_capability_descriptor_json(void)
{
    return DESCRIPTOR;
}
