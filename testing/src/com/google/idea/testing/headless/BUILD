load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "headless",
    testonly = 1,
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//shared",
        "//third_party/java/junit",
    ],
)
