load("//third_party/protobuf/build_defs:kt_jvm_proto_library.bzl", "kt_jvm_lite_proto_library")

proto_library(
    name = "proto",
    srcs = ["testproto.proto"],
)

kt_jvm_lite_proto_library(
    name = "proto_kt_proto_lite",
    deps = [":proto"],
)

java_library(
    name = "kotlinprotodep",
    srcs = ["TestKotlinProtoDep.java"],
    deps = [":proto_kt_proto_lite"],
)
