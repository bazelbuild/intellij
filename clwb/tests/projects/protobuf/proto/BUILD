load("@protobuf//bazel:cc_proto_library.bzl", "cc_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

proto_library(
    name = "addressbook_proto",
    srcs = ["addressbook.proto"],
    deps = ["@protobuf//:timestamp_proto"],
)

cc_proto_library(
    name = "addressbook_cc_proto",
    visibility = ["//visibility:public"],
    deps = [":addressbook_proto"],
)
