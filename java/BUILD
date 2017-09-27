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
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

java_library(
    name = "java",
    srcs = glob(["src/**/*.java"]),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//common/actionhelper"],
    deps = [
        "//base",
        "//common/experiments",
        "//common/guava",
        "//intellij_platform_sdk:junit",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//sdkcompat",
        "@jsr305_annotations//jar",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-java.xml"],
    visibility = ["//visibility:public"],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "//base:plugin_xml",
    ] + [
        ":plugin_xml",
    ],
)

stamped_plugin_xml(
    name = "java_plugin_xml",
    plugin_id = "com.google.idea.blaze.java",
    plugin_name = "com.google.idea.blaze.java",
    plugin_xml = "merged_plugin_xml",
)

optional_plugin_xml(
    name = "optional_xml",
    module = "JUnit",
    plugin_xml = "src/META-INF/java-contents.xml",
    visibility = ["//visibility:public"],
)

intellij_plugin(
    name = "java_integration_test_plugin",
    testonly = 1,
    optional_plugin_xmls = [":optional_xml"],
    plugin_xml = ":java_plugin_xml",
    deps = [
        ":java",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.java",
    deps = [
        ":java",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.java",
    test_package_root = "com.google.idea.blaze.java",
    runtime_deps = [
        ":java_integration_test_plugin",
    ],
    deps = [
        ":java",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)
