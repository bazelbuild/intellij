java_library(
    name = "deps",
    srcs = glob(["*.java"]),
    visibility = [
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
        "//querysync:__subpackages__",
    ],
    deps = [
        ":artifact_tracker_state_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/cc:cc_compilation_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/java",
        "//querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "//shared:artifact",
        "//shared:proto",
        "//shared:vcs",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
        "@com_google_protobuf//:protobuf_java",
        "@error_prone_annotations//jar",
    ],
)

proto_library(
    name = "artifact_tracker_state_proto",
    srcs = ["artifact_tracker_state.proto"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project:snapshot_proto",
    ],
)

java_proto_library(
    name = "artifact_tracker_state_java_proto",
    visibility = [
        "//querysync:__subpackages__",
    ],
    deps = [":artifact_tracker_state_proto"],
)
