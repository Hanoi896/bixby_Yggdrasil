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
    )
else()
    message(WARNING "llama.cpp submodule not found! Check .gitmodules or git submodule update.")
    set(LLAMA_INCLUDE_DIRS ${CMAKE_CURRENT_SOURCE_DIR}/include)
endif()

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_INCLUDE_DIRS}
)

# 네이티브 JNI 및 로컬 데몬 라이브러리 타겟
add_library(yggdrasil_llm SHARED
    llama_jni_bridge.cpp
    ExaoneRuntime.cpp
    LocalHttpServer.cpp
)

target_link_libraries(yggdrasil_llm
    PRIVATE
    llama
    ggml
    android
    log
)
