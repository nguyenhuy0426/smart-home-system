cmake_minimum_required(VERSION 3.16.0)
set(EXTRA_COMPONENT_DIRS
    "${CMAKE_SOURCE_DIR}/src"
    "${CMAKE_SOURCE_DIR}/third_party/esp32-camera")
include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(node_camera)
