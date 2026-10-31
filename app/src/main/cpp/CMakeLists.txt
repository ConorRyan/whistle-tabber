cmake_minimum_required(VERSION 3.22.1)
project(tapaudio C)

# The rhythm tapper's low-latency output: AAudio, which is part of Android.
add_library(tapaudio SHARED tap_audio.c)
target_compile_options(tapaudio PRIVATE -Wall -Wextra -Werror)
target_link_libraries(tapaudio aaudio log)
