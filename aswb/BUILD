#
# Description: Builds ASwB for blaze and bazel
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
        "src/META-INF/aswb.xml",
        "//base:plugin_xml",
        "//cpp:plugin_xml",
        "//java:plugin_xml",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "src/META-INF/aswb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    include_product_code_in_stamp = True,
    plugin_id = "com.google.idea.bazel.aswb",
    plugin_name = "Android Studio with Bazel",
    plugin_xml = ":merged_plugin_xml",
    stamp_since_build = True,
    version = VERSION,
)

java_library(
    name = "aswb_lib",
    srcs = glob(["src/**/*.java"]),
    resources = glob(["resources/**/*"]),
    runtime_deps = [
        "//cpp",
    ],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/integration/**/*.java"]),
    deps = [
        "@jsr305_annotations//jar",
        "@junit//jar",
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
    test_package_root = "com.google.idea.blaze.android",
    deps = [
        ":aswb_lib",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    platform_prefix = "AndroidStudio",
    required_plugins = "com.google.idea.bazel.aswb",
    test_package_root = "com.google.idea.blaze.android",
    runtime_deps = [
        ":aswb_bazel",
    ],
    deps = [
        ":aswb_lib",
        ":integration_test_utils",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//cpp",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

intellij_plugin(
    name = "aswb_bazel",
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":aswb_lib",
    ],
)
