cmake_minimum_required(VERSION 3.10.2)
project(kitkat_shim)

add_library(kitkat_shim SHARED kitkat_shim.c)
target_link_libraries(kitkat_shim dl log)
