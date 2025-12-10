load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "TestRunner",
    testonly = 1,
    srcs = ["IntegrationTestRunner.kt"],
    visibility = ["//visibility:public"],
    deps = [
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@bazel_tools//tools/jdk:TestRunner",
    ],
)
