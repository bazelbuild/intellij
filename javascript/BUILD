licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
    "intellij_integration_test_suite",
)

java_library(
    name = "javascript",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//sdkcompat",
        "//third_party/javascript",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-javascript.xml"],
    visibility = ["//visibility:public"],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "JavaScript",
    plugin_xml = "src/META-INF/javascript-contents.xml",
    visibility = ["//visibility:public"],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "//base:plugin_xml",
        "//java:plugin_xml",
    ] + [
        ":plugin_xml",
    ],
)

stamped_plugin_xml(
    name = "javascript_plugin_xml",
    plugin_id = "com.google.idea.blaze.javascript",
    plugin_name = "com.google.idea.blaze.javascript",
    plugin_xml = ":merged_plugin_xml",
)

intellij_plugin(
    name = "javascript_integration_test_plugin",
    testonly = 1,
    optional_plugin_xmls = [
        ":optional_xml",
        "//java:optional_java",  # needed for default workspace
    ],
    plugin_xml = ":javascript_plugin_xml",
    deps = [":javascript"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze",
    deps = [
        ":javascript",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    platform_prefix = "",
    required_plugins = "com.google.idea.blaze.javascript,JavaScript",
    test_package_root = "com.google.idea.blaze",
    runtime_deps = [":javascript_integration_test_plugin"],
    deps = [
        ":javascript",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//third_party/javascript:javascript_for_tests",
        "@junit//jar",
    ],
)
