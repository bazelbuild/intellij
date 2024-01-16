load(
    "//:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
)
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

licenses(["notice"])

java_library(
    name = "properties",
    srcs = glob(["src/**/*.java"]),
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.common.ui.properties",
    deps = [
        ":properties",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
    ],
)
