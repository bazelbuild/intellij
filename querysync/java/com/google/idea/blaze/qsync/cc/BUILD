load("@rules_java//java:defs.bzl", "java_library")
load("//third_party/protobuf/bazel:java_proto_library.bzl", "java_proto_library")
load("//third_party/protobuf/bazel:proto_library.bzl", "proto_library")

package(default_visibility = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    "//querysync:__subpackages__",
])

java_library(
    name = "cc",
    srcs = glob(["*.java"]),
    deps = [
        ":cc_compilation_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)

proto_library(
    name = "cc_compilation_info_proto",
    srcs = ["cc_compilation_info.proto"],
    visibility = [
        "//proto:__pkg__",
        "//querysync/java/com/google/idea/blaze/qsync/deps:__pkg__",
    ],
)

java_proto_library(
    name = "cc_compilation_info_java_proto",
    deps = [":cc_compilation_info_proto"],
)
