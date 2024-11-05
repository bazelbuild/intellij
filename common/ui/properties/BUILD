load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
    "DEFAULT_TEST_VISIBILITY",
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
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:jsr305",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.common.ui.properties",
    visibility = DEFAULT_TEST_VISIBILITY,
    runtime_deps = [
        "//intellij_platform_sdk:guava",
    ],
    deps = [
        ":properties",
        "//third_party/java/junit",
        "//intellij_platform_sdk:test_libs",
    ],
)
