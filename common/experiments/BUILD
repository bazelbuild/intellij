load(
    "//:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
    "G3PLUGINS_VISIBILITY",
    "SERVICES_EXPERIMENT_SUBPACKAGES",
)
load("//build_defs:build_defs.bzl", "intellij_plugin_library")
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

licenses(["notice"])

java_library(
    name = "experiments",
    srcs = glob(["src/**/*.java"]),
    resources = [":experiment_properties"],
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        "//common/util:platform",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/auto_value",
    ],
)

filegroup(
    name = "experiment_properties",
    srcs = ["src/com/google/idea/common/experiments/experiment.properties"],
    visibility = SERVICES_EXPERIMENT_SUBPACKAGES,
)

# includes an test ExperimentService registered in plugin XML. This is required
# for tests of code hit during test fixture setup (before the test code itself
# can register services / components)
intellij_plugin_library(
    name = "mock_experiment_service",
    testonly = 1,
    plugin_xmls = ["tests/utils/mock_experiment_service.xml"],
    visibility = G3PLUGINS_VISIBILITY,
    deps = [":unit_test_utils"],
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/**/*.java"]),
    visibility = COMMON_PLUGINS_VISIBILITY,
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
        "@com_google_guava_guava//jar",
        "@junit_junit//jar",
    ],
)
