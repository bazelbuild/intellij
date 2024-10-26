load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = [
    "//querysync:__subpackages__",
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
])

proto_library(
    name = "querysummary",
    srcs = ["querysummary.proto"],
)

java_proto_library(
    name = "querysummary_java_proto",
    visibility = ["//querysync:__subpackages__"],
    deps = [":querysummary"],
)

java_library(
    name = "query",
    srcs = glob(["*.java"]),
    deps = [
        ":querysummary_java_proto",
        "//shared",
        "//shared:proto",
        "//third_party/bazel/src/main/protobuf:build_java_proto",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)
