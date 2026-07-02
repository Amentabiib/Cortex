cmake_minimum_required(VERSION 3.22.1)
project(cortex_llama)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

add_subdirectory(${LLAMA_DIR} llama_build)

add_library(cortex_native SHARED
    cortex_jni.cpp
)

target_link_libraries(cortex_native
    llama
    common
    log
    android
)

target_include_directories(cortex_native PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
)
