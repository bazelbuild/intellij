load("//tools:java_grpc_library.bzl", "java_rpc_toolchain")
load("@bazel_skylib//rules:native_binary.bzl", "native_binary")

java_library(
    name = "java_grpc_library_deps__do_not_reference",
    exports = [
        "@jsr305_annotations//jar",
        "@com_google_guava_guava//jar",
        "@com_google_protobuf//:protobuf_java",
        "@io_grpc_grpc_api//jar",
        "@io_grpc_grpc_protobuf//jar",
        "@io_grpc_grpc_stub//jar",
        "@org_apache_tomcat_annotations_api//jar",
    ],
)

java_library(
    name = "java_lite_grpc_library_deps__do_not_reference",
    exports = [
        "@jsr305_annotations//jar",
        "@com_google_guava_guava//jar",
        "@io_grpc_grpc_api//jar",
        "@io_grpc_grpc_protobuf-lite//jar",
        "@io_grpc_grpc_stub//jar",
        "@org_apache_tomcat_annotations_api//jar",
    ],
)

java_rpc_toolchain(
    name = "java_grpc_library_toolchain",
    plugin = ":protoc_gen_grpc_java",
    runtime = [":java_grpc_library_deps__do_not_reference"],
    visibility = ["//visibility:public"],
)

java_rpc_toolchain(
    name = "java_lite_grpc_library_toolchain",
    plugin = ":protoc_gen_grpc_java",
    plugin_arg = "lite",
    runtime = [":java_lite_grpc_library_deps__do_not_reference"],
    visibility = ["//visibility:public"],
)

native_binary(
    name = "protoc_gen_grpc_java",
    src = "@protoc_gen_grpc_java//file",
    out = "protoc-gen-grpc-java",
)
