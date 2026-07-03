# This file was automatically generated for projects
# without default 'CMakeLists.txt' file.

FILE(GLOB_RECURSE app_sources ${CMAKE_SOURCE_DIR}/src/*.*)

idf_component_register(
    SRCS ${app_sources}
    INCLUDE_DIRS "../include"
    REQUIRES
        bt
        driver
        esp_driver_gpio
        esp_driver_spi
        esp_driver_uart
        esp_event
        esp_http_client
        esp_netif
        esp_timer
        esp_wifi
        lwip
        mbedtls
        nvs_flash
)
