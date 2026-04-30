load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "actions",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//sdkcompat",
        "@error_prone_annotations//jar",
    ],
)
