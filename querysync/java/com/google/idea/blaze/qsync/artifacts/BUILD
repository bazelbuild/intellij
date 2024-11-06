load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "artifacts",
    srcs = glob(["*.java"]),
    visibility = [
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
        "//querysync:__subpackages__",
    ],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//shared",
        "//shared:artifact",
        "//shared:exception",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
        "@protobuf//:protobuf_java",
        "@jsr305_annotations//jar",
        "@protobuf//:protobuf_java",
    ],
)
