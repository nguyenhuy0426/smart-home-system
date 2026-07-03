cmake_minimum_required(VERSION 3.16)
project(access_security_tests C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

find_package(OpenSSL REQUIRED)

add_executable(access_security_tests
    test_access_security.c
    ../src/access_authorization.c
    ../src/access_credential_privacy.c
    ../src/mfrc522_protocol.c
    ../src/tzm1026_protocol.c
)
target_include_directories(access_security_tests PRIVATE ../include)
target_link_libraries(access_security_tests PRIVATE OpenSSL::Crypto)
target_compile_options(access_security_tests PRIVATE -Wall -Wextra -Werror)

enable_testing()
add_test(NAME access_security_tests COMMAND access_security_tests)
