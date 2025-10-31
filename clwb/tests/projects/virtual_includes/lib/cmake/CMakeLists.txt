cmake_minimum_required(VERSION 3.5.0)
project(cmake_lib CXX)

add_library(cmake STATIC cmake_lib.cc)

install(TARGETS cmake ARCHIVE DESTINATION lib)
install(FILES cmake_lib.h DESTINATION include)