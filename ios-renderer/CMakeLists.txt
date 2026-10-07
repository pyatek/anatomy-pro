# Builds the Objective-C++ Filament host as a static library for one iOS slice.
#
# Driven by Gradle rather than Xcode so that `./gradlew iosSimulatorArm64Test` builds the
# whole chain from the command line, which is what lets the renderer-api contract tests
# run against the real renderer in CI (spec §15).
cmake_minimum_required(VERSION 3.24)
project(AnatomyRenderer LANGUAGES CXX OBJCXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT FILAMENT_SDK)
    message(FATAL_ERROR "FILAMENT_SDK must point at the unpacked Filament release")
endif()

add_library(AnatomyRenderer STATIC src/AnatomyRenderer.mm src/OutlinePass.mm)

target_include_directories(AnatomyRenderer
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include
    PRIVATE ${FILAMENT_SDK}/include)

target_compile_options(AnatomyRenderer PRIVATE -fobjc-arc -fvisibility=default)
