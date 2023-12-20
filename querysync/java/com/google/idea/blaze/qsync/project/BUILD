package(default_visibility = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    "//querysync:__subpackages__",
])

proto_library(
    name = "language_class_proto",
    srcs = ["language_class.proto"],
)

java_proto_library(
    name = "language_class_java_proto",
    visibility = [
        "//base:__subpackages__",
        "//cpp:__subpackages__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
        "//querysync:__subpackages__",
    ],
    deps = [":language_class_proto"],
)

proto_library(
    name = "project_proto",
    srcs = ["project.proto"],
    deps = [":language_class_proto"],
)

java_proto_library(
    name = "project_java_proto",
    visibility = [
        "//base:__subpackages__",
        "//cpp:__subpackages__",
        "//querysync:__subpackages__",
    ],
    deps = [":project_proto"],
)

proto_library(
    name = "snapshot_proto",
    srcs = ["snapshot.proto"],
    deps = [
        ":language_class_proto",
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
        ":language_class_java_proto",
        ":project_java_proto",
        ":snapshot_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//shared",
        "//shared:vcs",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_errorprone_error_prone_annotations//jar",
        "@com_google_code_findbugs_jsr305//jar",
    ],
)
