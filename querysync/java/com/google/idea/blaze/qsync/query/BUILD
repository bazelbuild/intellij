load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    "//querysync:__subpackages__",
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

kt_jvm_library(
    name = "query",
    srcs = glob(["*.java"]),
    deps = [
        ":querysummary_java_proto",
        "//proto:proto_deps",
        "//shared",
        "//shared:proto",
        "//third_party/java/auto_value",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)
