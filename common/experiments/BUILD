licenses(["notice"])  # Apache 2.0

java_library(
    name = "experiments",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
        "@jsr305_annotations//jar",
    ],
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/unit/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":experiments",
        "//intellij_platform_sdk:plugin_api",
        "@jsr305_annotations//jar",
    ],
)

load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.common.experiments",
    deps = [
        ":experiments",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@junit//jar",
    ],
)
