licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "merged_plugin_xml",
    "stamped_plugin_xml",
    "intellij_plugin",
    "optional_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
    "intellij_integration_test_suite",
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-kotlin.xml"],
    visibility = ["//visibility:public"],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.jetbrains.kotlin",
    plugin_xml = "src/META-INF/kotlin-contents.xml",
    visibility = ["//visibility:public"],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        ":plugin_xml",
        "//base:plugin_xml",
        "//java:plugin_xml",
    ],
)

stamped_plugin_xml(
    name = "kotlin_plugin_xml",
    plugin_id = "com.google.idea.blaze.kotlin",
    plugin_name = "com.google.idea.blaze.kotlin",
    plugin_xml = "merged_plugin_xml",
)

java_library(
    name = "kotlin",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:kotlin",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//sdkcompat",
    ],
)

intellij_plugin(
    name = "kotlin_integration_test_plugin",
    testonly = 1,
    optional_plugin_xmls = [
        ":optional_xml",
        "//java:optional_java",
    ],
    plugin_xml = ":kotlin_plugin_xml",
    deps = [":kotlin"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze",
    deps = [
        ":kotlin",
        "//base",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:kotlin_for_tests",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "org.jetbrains.kotlin,com.google.idea.blaze.kotlin",
    test_package_root = "com.google.idea.blaze.kotlin",
    runtime_deps = [":kotlin_integration_test_plugin"],
    deps = [
        ":kotlin",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:kotlin_for_tests",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "//sdkcompat",
        "@junit//jar",
    ],
)
