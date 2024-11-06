load("@protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

proto_library(
    name = "proto",
    srcs = ["only.proto"],
)

java_lite_proto_library(
    name = "proto_java_proto_lite",
    deps = [":proto"],
)
