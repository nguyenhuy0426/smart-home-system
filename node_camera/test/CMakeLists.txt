cmake_minimum_required(VERSION 3.16)
project(camera_protocol_tests C)

enable_testing()
add_executable(camera_protocol_tests
    test_camera_protocol.c
    ../src/camera_protocol.c
    ../src/provisioning_parser.c)
target_include_directories(camera_protocol_tests PRIVATE ../include)
target_compile_features(camera_protocol_tests PRIVATE c_std_11)
target_compile_definitions(camera_protocol_tests PRIVATE _POSIX_C_SOURCE=200809L)
target_compile_options(camera_protocol_tests PRIVATE -Wall -Wextra -Werror -pedantic)
add_test(NAME camera_protocol_tests COMMAND camera_protocol_tests)
