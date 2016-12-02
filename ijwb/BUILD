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
        "//java:plugin_xml",
        "//plugin_dev:plugin_xml",
    ],
    visibility = ["//visibility:public"],
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
    visibility = ["//visibility:public"],
    exports = [
        "//plugin_dev",
    ],
    deps = [
        "//base",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "@jsr305_annotations//jar",
    ],
)

intellij_plugin(
    name = "ijwb_bazel",
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":ijwb_lib",
    ],
)

load(
    "//testing:test_defs.bzl",
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
