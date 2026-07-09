cmake_minimum_required(VERSION 3.22)
project(whisperbar CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp liegt als Git-Submodul in der Repo-Wurzel.
# Von app/src/main/cpp/ aus sind das vier Ebenen nach oben.
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper.cpp)

if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "whisper.cpp-Submodul fehlt unter ${WHISPER_DIR}. "
        "Ausfuehren: git submodule update --init --recursive")
endif()

# whisper.cpp/ggml-Build fuer Android schlank + reproduzierbar halten.
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2           OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)  # NDK hat kein libomp -> aus
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)  # Cross-Compile: kein -march=native
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)  # whisper+ggml statisch in unsere .so linken

# Baut die Targets `whisper` und `ggml` (statisch) mit whisper.cpps eigenem,
# gepflegtem Android/NDK-CMake. Kein manuelles Quell-Listing noetig.
add_subdirectory(${WHISPER_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp)

find_library(LOG_LIB log)

add_library(whisperbar SHARED whisper_jni.cpp)

# `whisper` propagiert seine PUBLIC-Includes (whisper.h) und ggml transitiv.
target_link_libraries(whisperbar ${LOG_LIB} android whisper)
