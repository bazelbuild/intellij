load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "integration",
    testonly = 1,
    srcs = glob(["*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//third_party/java/junit",
    ],
)
