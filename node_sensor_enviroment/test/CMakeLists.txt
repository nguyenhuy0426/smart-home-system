cmake_minimum_required(VERSION 3.16)
project(environment_safety_tests C)

add_executable(environment_safety_tests
    test_environment_safety.c
    ../src/adc_sample_math.c
    ../src/bme680_status.c
    ../src/dht22_protocol.c
    ../src/environment_sensor_fusion.c
    ../src/environment_sensor_pipeline.c
    ../src/gp2y1014_conversion.c
    ../src/mq7_conversion.c
    ../src/mq7_cycle.c
    ../src/provisioning_parser.c
    ../src/sensor_status.c
)
target_include_directories(environment_safety_tests PRIVATE
    ../include
    ../third_party/bosch_bme68x
)
target_compile_options(environment_safety_tests PRIVATE
    -Wall -Wextra -Werror -pedantic
)
target_link_libraries(environment_safety_tests PRIVATE m)

enable_testing()
add_test(NAME environment_safety_tests COMMAND environment_safety_tests)
