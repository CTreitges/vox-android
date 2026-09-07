cmake_minimum_required(VERSION 3.22)
project(vox CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp liegt als Git-Submodul in der Repo-Wurzel (Tag v1.9.3, Commit 371b5a75).
# Von app/src/main/cpp/ aus sind das vier Ebenen nach oben.
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper.cpp)
if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "whisper.cpp-Submodul fehlt unter ${WHISPER_DIR}. "
        "Ausfuehren: git submodule update --init --recursive")
endif()

# --- whisper.cpp / ggml schlank & reproduzierbar (Option-Namen in v1.9.3 geprueft) ---
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2           OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)  # ggml-eigener Threadpool; NDK-OpenMP via CMake nicht unterstuetzt
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)  # Cross-Compile: kein -march=native
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)  # "not supported on Android" (llama.cpp docs/build.md)
set(GGML_CPU_KLEIDIAI      OFF CACHE BOOL "" FORCE)  # deckt Q5_1 nicht ab, laedt Tarball zur Configure-Zeit
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)  # whisper+ggml statisch in libvox.so

# Ziel-ISA: Armv8.2 + FP16-Vektorarithmetik + DotProd (Cortex-A55/A75 und neuer, ab 2018).
# Kein +i8mm (Armv8.6, SIGILL auf 2018-2021-SoCs). Kotlin (OfflineSupport) prueft /proc/cpuinfo
# auf "fphp" + "asimddp", bevor die Bibliothek ueberhaupt geladen wird.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
endif()

# Baut die Targets `whisper` und `ggml*` (statisch) mit whisper.cpps eigenem, gepflegtem CMake.
# EXCLUDE_FROM_ALL: nur das, was libvox tatsaechlich linkt.
add_subdirectory(${WHISPER_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp EXCLUDE_FROM_ALL)

find_library(LOG_LIB log)

add_library(vox SHARED whisper_jni.cpp)
# `whisper` propagiert seine PUBLIC-Includes (whisper.h) und ggml transitiv.
target_link_libraries(vox PRIVATE ${LOG_LIB} android whisper)

# Groesse/Geschwindigkeit wie examples/whisper.android (v1.9.3)
foreach(t vox whisper ggml ggml-base ggml-cpu)
    if(TARGET ${t})
        target_compile_options(${t} PRIVATE
            $<$<NOT:$<CONFIG:Debug>>:-O3>
            -fvisibility=hidden -fvisibility-inlines-hidden
            -ffunction-sections -fdata-sections)
    endif()
endforeach()
target_link_options(vox PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    # 16-KB-Page-Size: ab NDK r28 Default, fuer NDK r27 (LTS-Fallback) Pflicht; redundant unschaedlich.
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384)
