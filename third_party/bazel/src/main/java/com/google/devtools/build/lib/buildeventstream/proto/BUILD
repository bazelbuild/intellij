load("@com_google_protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_proto_library")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//third_party/bazel/src/main/java/com/google/devtools/build/lib:__pkg__"],
)

java_proto_library(
    name = "build_event_stream_java_proto",
    deps = ["build_event_stream_proto"],
)

proto_library(
    name = "build_event_stream_proto",
    srcs = ["build_event_stream.proto"],
    visibility = ["//visibility:public"],
    deps = [
        "//third_party/bazel/src/main/java/com/google/devtools/build/lib/packages/metrics:package_load_metrics_proto",
        "//third_party/bazel/src/main/protobuf:action_cache_proto",
        "//third_party/bazel/src/main/protobuf:command_line_proto",
        "//third_party/bazel/src/main/protobuf:failure_details_proto",
        "//third_party/bazel/src/main/protobuf:invocation_policy_proto",
        "@com_google_protobuf//:any_proto",
        "@com_google_protobuf//:duration_proto",
        "@com_google_protobuf//:timestamp_proto",
    ],
)
