#
# Description: Builds ijwb
#

licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
    "optional_plugin_xml",
    "plugin_deploy_zip",
    "repackaged_files",
    "stamped_plugin_xml",
)
load(
    "//build_defs:intellij_plugin_debug_target.bzl",
    "intellij_plugin_debug_target",
)
load("//:version.bzl", "VERSION")
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

merged_plugin_xml(
    name = "merged_plugin_xml_common",
    srcs = [
        "src/META-INF/ijwb.xml",
        "//base:plugin_xml",
        "//dart:plugin_xml",
        "//golang:plugin_xml",
        "//java:plugin_xml",
        "//kotlin:plugin_xml",
        "//python:plugin_xml",
        "//scala:plugin_xml",
        "//skylark:plugin_xml",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "src/META-INF/ijwb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

optional_plugin_xml(
    name = "optional_java",
    module = "com.intellij.modules.java",
    plugin_xml = "src/META-INF/java-contents.xml",
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    # Supports multiple IDEs, so no product code included
    include_product_code_in_stamp = False,
    plugin_id = "com.google.idea.bazel.ijwb",
    plugin_name = "Bazel",
    plugin_xml = ":merged_plugin_xml",
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

java_library(
    name = "ijwb_lib",
    srcs = glob(["src/**/*.java"]),
    exports = [
        "//plugin_dev",
    ],
    runtime_deps = [
        "//dart",
        "//golang",
        "//kotlin",
        "//python",
        "//scala",
        "//skylark",
        "//terminal",
    ],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//sdkcompat",
    ],
)

OPTIONAL_PLUGIN_XMLS = [
    ":optional_java",
    "//golang:optional_xml",
    "//java:optional_java",
    "//java:optional_coverage",
    "//kotlin:optional_xml",
    "//plugin_dev:optional_xml",
    "//python:optional_xml",
    "//scala:optional_xml",
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

repackaged_files(
    name = "plugin_jar",
    srcs = [":ijwb_bazel"],
    prefix = "ijwb/lib",
)

repackaged_files(
    name = "aspect_directory",
    srcs = ["//aspect:aspect_files"],
    prefix = "ijwb/aspect",
    strip_prefix = "/aspect",
)

intellij_plugin_debug_target(
    name = "ijwb_bazel_dev",
    deps = [
        ":aspect_directory",
        ":plugin_jar",
    ],
)

plugin_deploy_zip(
    name = "ijwb_bazel_zip",
    srcs = [
        ":aspect_directory",
        ":plugin_jar",
    ],
    zip_filename = "ijwb_bazel.zip",
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
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.bazel.ijwb",
    test_package_root = "com.google.idea.blaze.ijwb",
    runtime_deps = [
        ":ijwb_bazel",
    ],
    deps = [
        ":ijwb_lib",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@junit//jar",
    ],
)
