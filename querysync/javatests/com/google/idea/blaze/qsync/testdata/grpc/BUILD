load("@rules_java//java:defs.bzl", "java_library")
load("//third_party/java/grpc:build_defs.bzl", "java_grpc_library")

proto_library(
    name = "test_service_proto",
    srcs = ["testservice.proto"],
)

java_lite_proto_library(
    name = "test_service_java_proto_lite",
    deps = [":test_service_proto"],
)

java_grpc_library(
    name = "test_service_java_grpc_lite",
    srcs = [":test_service_proto"],
    flavor = "lite",
    deps = [":test_service_java_proto_lite"],
)

java_library(
    name = "grpc",
    srcs = ["TestGrpcClass.java"],
    deps = [":test_service_java_grpc_lite"],
)
