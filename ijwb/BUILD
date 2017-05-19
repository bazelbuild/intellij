#
# Description: Builds ijwb
#

licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
    "stamped_plugin_xml",
)
load("//:version.bzl", "VERSION")

merged_plugin_xml(
    name = "merged_plugin_xml_common",
    srcs = [
        "src/META-INF/ijwb.xml",
        "//base:plugin_xml",
        "//golang:plugin_xml",
        "//java:plugin_xml",
        "//plugin_dev:plugin_xml",
        "//python:plugin_xml",
        "//scala:plugin_xml",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "src/META-INF/ijwb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    include_product_code_in_stamp = True,
    plugin_id = "com.google.idea.bazel.ijwb",
    plugin_name = "IntelliJ with Bazel",
    plugin_xml = ":merged_plugin_xml",
    stamp_since_build = True,
    version = VERSION,
)

java_library(
    name = "ijwb_lib",
    srcs = glob(["src/**/*.java"]),
    exports = [
        "//plugin_dev",
    ],
    runtime_deps = [
        "//golang",
        "//python",
        "//terminal",
    ],
    deps = [
        "//base",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//scala",
        "//sdkcompat",
        "@jsr305_annotations//jar",
    ],
)

OPTIONAL_PLUGIN_XMLS = [
    "//scala:optional_xml",
    "//python:optional_xml",
    "//terminal:optional_xml",
]

intellij_plugin(
    name = "ijwb_bazel",
    optional_plugin_xmls = OPTIONAL_PLUGIN_XMLS,
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":ijwb_lib",
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
    test_package_root = "com.google.idea.blaze.ijwb",
    deps = [
        ":ijwb_lib",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.ijwb",
    test_package_root = "com.google.idea.blaze.ijwb",
    runtime_deps = [
        ":ijwb_bazel",
    ],
    deps = [
        ":ijwb_lib",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)
