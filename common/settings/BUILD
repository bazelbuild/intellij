load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "settings",
    srcs = glob(["src/**/*.java"]),
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        "//third_party/java/auto_value",
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:jsr305",
        "@error_prone_annotations//jar",
    ],
)
