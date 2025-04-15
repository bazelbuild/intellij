load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    "//querysync:__subpackages__",
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

kt_jvm_library(
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
        "@error_prone_annotations//jar",
        "@jsr305_annotations//jar",
    ],
)
