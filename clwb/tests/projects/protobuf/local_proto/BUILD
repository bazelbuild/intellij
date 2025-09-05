load("@protobuf//bazel:cc_proto_library.bzl", "cc_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

proto_library(
    name = "animal_proto",
    srcs = ["animal.proto"],
)

cc_proto_library(
    name = "animal_cc_proto",
    visibility = ["//visibility:public"],
    deps = [":animal_proto"],
)
