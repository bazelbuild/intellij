licenses(["notice"])  # Apache 2.0

java_library(
    name = "base",
    srcs = glob(["src/**/*.java"]),
    resources = glob(["resources/**/*"]),
    visibility = ["//visibility:public"],
    deps = [
        "//common/experiments",
        "//intellij_platform_sdk:plugin_api",
        "//proto_deps",
        "@jsr305_annotations//jar",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-base.xml"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/unit/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/integration/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":unit_test_utils",
        "//base",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//proto_deps",
        "//testing:lib",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

load(
    "//build_defs:build_defs.bzl",
    "stamped_plugin_xml",
    "intellij_plugin",
)

stamped_plugin_xml(
    name = "base_plugin_xml",
    plugin_id = "com.google.idea.blaze.base",
    plugin_name = "com.google.idea.blaze.base",
    plugin_xml = "plugin_xml",
)

intellij_plugin(
    name = "base_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":base_plugin_xml",
    deps = [
        ":base",
    ],
)

load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.base",
    deps = [
        ":base",
        ":unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//proto_deps",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.base",
    test_package_root = "com.google.idea.blaze.base",
    runtime_deps = [
        ":base_integration_test_plugin",
    ],
    deps = [
        ":base",
        ":integration_test_utils",
        ":unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//proto_deps",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)
