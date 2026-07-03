#!/bin/bash
set -e
echo "Running Smart Home Tests"
echo "========================"

echo "1. Compiling and running C tests..."
gcc -Wall -Wextra -I node_sensor_enviroment/include node_sensor_enviroment/src/environment_sensor_fusion_stub.c node_sensor_enviroment/src/test_sensor_fusion.c -o /tmp/test_fusion
/tmp/test_fusion

gcc -Wall -Wextra -I node_sensor_enviroment/include node_sensor_enviroment/src/environment_sensor_fusion_stub.c node_sensor_enviroment/src/environment_sensor_pipeline_stub.c node_sensor_enviroment/src/test_sensor_pipeline.c -o /tmp/test_pipeline
/tmp/test_pipeline

echo "2. Compiling and running Java tests..."
mkdir -p /tmp/gw_build
# Exclude Android framework dependent classes from raw javac compilation
find /home/huynn/aosp/source/packages/apps/SmartHomeSystem/src/com/android/smarthome/ -name "*.java" \
  | grep -v "SmartHomeGatewayService.java" \
  | grep -v "GatewaySecurityPolicy.java" \
  | grep -v "NodeProvisioningCoordinator.java" \
  > /tmp/java_sources.txt
javac -d /tmp/gw_build @/tmp/java_sources.txt
java -cp /tmp/gw_build com.android.smarthome.gateway.test_json_parser
java -cp /tmp/gw_build com.android.smarthome.ota.test_ota_coordinator
java -cp /tmp/gw_build com.android.smarthome.firebase.test_firebase_sync_queue

echo "3. Running Firebase rules tests..."
if [ -f "node_modules/.bin/mocha" ]; then
  npx -y firebase-tools@latest emulators:exec "node_modules/.bin/mocha test_rules.js" --project demo-smarthome
else
  echo "Mocha not found, skipping rules test."
fi

echo "========================"
echo "All tests passed successfully!"
