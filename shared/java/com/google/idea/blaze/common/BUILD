load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])

java_library(
    name = "common",
    srcs = glob(["*.java"]),
    visibility = ["//shared:__subpackages__"],
    deps = [
        "@jsr305_annotations//jar",
        "//third_party/java/auto_value",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)
