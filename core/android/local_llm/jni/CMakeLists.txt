cmake_minimum_required(VERSION 3.22.1)
project(yggdrasil_llm_daemon LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Qualcomm Snapdragon 8 Elite NPU (QNN/Hexagon) 및 Vulkan GPU 최적화 플래그
add_definitions(-DGGML_USE_VULKAN=1)
add_definitions(-DGGML_USE_QNN=1)
add_definitions(-DGGML_QNN_ENABLE_HEXAGON=1)

# ARMv8.4-A+ 및 NEON/DotProd/FP16 벡터화 가속
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -flto -march=armv8.4-a+dotprod+fp16")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto -march=armv8.4-a+dotprod+fp16")

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# 네이티브 JNI 및 로컬 데몬 라이브러리
add_library(yggdrasil_llm SHARED
    llama_jni_bridge.cpp
    ExaoneRuntime.cpp
    LocalHttpServer.cpp
)

target_link_libraries(yggdrasil_llm
    android
    log
)
