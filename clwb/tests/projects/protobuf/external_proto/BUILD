load("@protobuf//bazel:cc_proto_library.bzl", "cc_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

proto_library(
    name = "person_proto",
    srcs = ["person.proto"],
    deps = ["@protobuf//:timestamp_proto"],
)

cc_proto_library(
    name = "person_cc_proto",
    visibility = ["//visibility:public"],
    deps = [":person_proto"],
)
