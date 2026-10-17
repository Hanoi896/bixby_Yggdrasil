cmake_minimum_required(VERSION 3.22.1)
project(yggdrasil_llm_daemon LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp 모바일 임베디드 빌드 플래그
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Do not build tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Do not build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Do not build server" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "Do not build common CLI tools" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static llama libraries" FORCE)

# [과제 4 실전 구현] Snapdragon 8 Elite 하드웨어 가속 백엔드 활성화
# 1. Vulkan GPU 가속 백엔드 (NDK 호스트 glslc 의존성 회피를 위해 기본 OFF)
set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan backend for Android NDK" FORCE)

# 2. Qualcomm Hexagon HTP / QNN NPU 백엔드 (HEXAGON_SDK_ROOT 환경변수 존재 시 활성화, 부재 시 ARM NEON CPU 백엔드로 안전 폴백)
if (DEFINED ENV{HEXAGON_SDK_ROOT} AND IS_DIRECTORY "$ENV{HEXAGON_SDK_ROOT}")
    set(GGML_HEXAGON ON CACHE BOOL "Enable Qualcomm Hexagon HTP NPU backend for Snapdragon 8 Elite" FORCE)
    set(GGML_HEXAGON_ARCH "v75;v79;v81" CACHE STRING "Target Hexagon NPU architectures" FORCE)
else()
    set(GGML_HEXAGON OFF CACHE BOOL "Disable Hexagon backend when Hexagon SDK is not installed" FORCE)
endif()

# Snapdragon 8 Elite 및 ARMv8.4-A 최적화
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP on Android for battery/thermal care" FORCE)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -flto")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto")
endif()

# llama.cpp 서브모듈 디렉터리 경로
set(LLAMA_ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

if (EXISTS ${LLAMA_ROOT_DIR}/CMakeLists.txt)
    add_subdirectory(${LLAMA_ROOT_DIR} llama_build EXCLUDE_FROM_ALL)
    set(LLAMA_INCLUDE_DIRS
        ${LLAMA_ROOT_DIR}/include
        ${LLAMA_ROOT_DIR}/ggml/include
        ${LLAMA_ROOT_DIR}/ggml/src/ggml-hexagon
    )
else()
    message(WARNING "llama.cpp submodule not found! Check .gitmodules or git submodule update.")
    set(LLAMA_INCLUDE_DIRS ${CMAKE_CURRENT_SOURCE_DIR}/include)
endif()

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_INCLUDE_DIRS}
)

# 네이티브 JNI 라이브러리 타겟 (HTTP 소켓 서버 완전 제거, JNI 메모리 직결)
add_library(yggdrasil_llm SHARED
    llama_jni_bridge.cpp
    ExaoneRuntime.cpp
)

target_link_libraries(yggdrasil_llm
    PRIVATE
    llama
    ggml
    android
    log
)

