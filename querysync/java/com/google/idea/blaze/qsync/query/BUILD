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

java_library(
    name = "query",
    srcs = glob(["*.java"]),
    deps = [
        ":querysummary_java_proto",
        "//shared",
        "//third_party/auto_value",
        "//third_party/bazel/src/main/protobuf:build_java_proto",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)
