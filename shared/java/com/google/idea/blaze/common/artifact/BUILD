load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])

java_library(
    name = "artifact",
    srcs = glob(["*.java"]),
    visibility = ["//shared:__subpackages__"],
    deps = [
        "@jsr305_annotations//jar",
        "//shared",
        "//intellij_platform_sdk:plugin_api",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)
