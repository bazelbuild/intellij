load("//build_defs:build_defs.bzl", "intellij_plugin_library")
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "experiments",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
    ],
)

# includes an test ExperimentService registered in plugin XML. This is required
# for tests of code hit during test fixture setup (before the test code itself
# can register services / components)
intellij_plugin_library(
    name = "mock_experiment_service",
    testonly = 1,
    plugin_xmls = ["tests/utils/mock_experiment_service.xml"],
    deps = [":unit_test_utils"],
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.common.experiments",
    deps = [
        ":experiments",
        ":unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//testing:lib",
        "@junit//jar",
    ],
)
