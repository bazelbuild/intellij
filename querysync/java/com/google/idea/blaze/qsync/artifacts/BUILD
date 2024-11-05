load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "artifacts",
    srcs = glob(["*.java"]),
    visibility = [
        "//querysync:__subpackages__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
    deps = [
        ":build_artifact_java_proto",
        "@jsr305_annotations//jar",
        "//querysync/java/com/google/idea/blaze/qsync/deps:artifact_tracker_state_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//shared",
        "//shared:artifact",
        "//shared:exception",
        "//third_party/java/auto_value",
        "@com_google_guava_guava//jar",
        "@protobuf//:protobuf_java",
    ],
)

proto_library(
    name = "build_artifact_proto",
    srcs = ["build_artifact.proto"],
    visibility = [
        "//querysync/java/com/google/idea/blaze/qsync/cc:__pkg__",
        "//querysync/java/com/google/idea/blaze/qsync/java:__pkg__",
    ],
)

java_proto_library(
    name = "build_artifact_java_proto",
    visibility = [
        "//querysync/java/com/google/idea/blaze/qsync/cc:__pkg__",
        "//querysync/java/com/google/idea/blaze/qsync/java:__pkg__",
        "//querysync/javatests:__subpackages__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
    deps = [":build_artifact_proto"],
)
