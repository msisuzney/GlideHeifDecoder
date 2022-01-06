cmake_minimum_required(VERSION 3.18.1)
project(heifdecoder C CXX ASM)
include(FetchContent)

set(CMAKE_BUILD_TYPE Release)

add_library(heifdecoder SHARED
        heifdecoder.cpp
        row_convert.cpp
        )

get_filename_component(deps "../_deps" REALPATH BASE_DIR "${CMAKE_BINARY_DIR}")
set(FETCHCONTENT_BASE_DIR ${deps})

add_subdirectory(libheif)
add_definitions(-DHAVE_LIBHEIF)
target_link_libraries(heifdecoder android jnigraphics log)
