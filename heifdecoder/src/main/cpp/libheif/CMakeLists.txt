FetchContent_Declare(libheif
        GIT_REPOSITORY https://github.com/strukturag/libheif
        GIT_TAG v1.12.0
        PATCH_COMMAND echo "" > gdk-pixbuf/CMakeLists.txt # Avoid building gdk-pixbuf, we don't need it
        BINARY_DIR build
        SUBBUILD_DIR subbuild
        )

include(libde265.cmake)

include(dav1d.cmake)

option(BUILD_SHARED_LIBS "" OFF)
option(WITH_EXAMPLES "" OFF)
option(WITH_DAV1D "" OFF)
option(WITH_LIBDE265 "" OFF)
option(WITH_X265 "" OFF)
option(WITH_AOM "" OFF)
option(WITH_RAV1E "" OFF)

FetchContent_MakeAvailable(libheif)

target_include_directories(heifdecoder PRIVATE ${libheif_BINARY_DIR} ${libheif_SOURCE_DIR})
target_link_libraries(heifdecoder heif)
