package(default_visibility = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    "//querysync:__subpackages__",
])

proto_library(
    name = "project_proto",
    srcs = ["project.proto"],
)

java_proto_library(
    name = "project_java_proto",
    visibility = ["//querysync:__subpackages__"],
    deps = [":project_proto"],
)

proto_library(
    name = "snapshot_proto",
    srcs = ["snapshot.proto"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary",
    ],
)

java_proto_library(
    name = "snapshot_java_proto",
    visibility = ["//querysync:__subpackages__"],
    deps = [":snapshot_proto"],
)

java_library(
    name = "project",
    srcs = glob(["*.java"]),
    deps = [
        ":project_java_proto",
        ":snapshot_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/vcs",
        "//shared",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
        "@com_google_protobuf//:protobuf_java",
        "@error_prone_annotations//jar",
        "@jsr305_annotations//jar",
    ],
)
