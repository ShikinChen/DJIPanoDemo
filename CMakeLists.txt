cmake_minimum_required(VERSION 3.6)

set(ROOT ${CMAKE_CURRENT_LIST_DIR})

file(GLOB opencv_stitching ${ROOT}/sdk/native/staticlibs/${ANDROID_ABI}/libopencv_stitching.a)

include(sdk/native/jni/OpenCVConfig.cmake)
add_subdirectory(app/src/main/cpp)

