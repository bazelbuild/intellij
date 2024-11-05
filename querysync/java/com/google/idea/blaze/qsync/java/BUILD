load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = [
    "//querysync:__subpackages__",
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
])

proto_library(
    name = "java_target_info",
    srcs = ["java_target_info.proto"],
    visibility = [
        "//proto:__pkg__",
    ],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts:build_artifact_proto",
    ],
)

java_proto_library(
    name = "java_target_info_java_proto",
    deps = [":java_target_info"],
)

java_library(
    name = "java",
    srcs = glob(["*.java"]),
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/deps:artifact_tracker_state_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "//shared:artifact",
        "//third_party/java/auto_value",
        "@com_google_guava_guava//jar",
        "@jsr305_annotations//jar",
        "@error_prone_annotations//jar",
    ],
)
