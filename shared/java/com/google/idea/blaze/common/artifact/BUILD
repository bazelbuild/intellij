load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])

java_library(
    name = "artifact",
    srcs = glob(["*.java"]),
    visibility = [
        "//base:__subpackages__",
        "//shared:__subpackages__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
    deps = [
        "@jsr305_annotations//jar",
        "//shared",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)
