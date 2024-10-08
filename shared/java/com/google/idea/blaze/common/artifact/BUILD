load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])

java_library(
    name = "artifact",
    srcs = glob(["*.java"]),
    visibility = ["//shared:__subpackages__"],
    deps = [
        "//shared",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
        "@jsr305_annotations//jar",
    ],
)
