java_library(
    name = "qsync",
    srcs = glob(["*.java"]),
    visibility = ["//querysync:__subpackages__"],
    deps = [
        "//shared",
        "//third_party/auto_value",
        "//third_party/bazel/src/main/protobuf:build_java_proto",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
        "@jsr305_annotations//jar",
    ],
)
