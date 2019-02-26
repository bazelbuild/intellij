#
# Description: Builds clwb
#

load("//:version.bzl", "VERSION")
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
    "plugin_deploy_zip",
    "repackaged_files",
    "stamped_plugin_xml",
)
load(
    "//build_defs:intellij_plugin_debug_target.bzl",
    "intellij_plugin_debug_target",
)
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

licenses(["notice"])  # Apache 2.0

merged_plugin_xml(
    name = "merged_plugin_xml_common",
    srcs = [
        "src/META-INF/clwb.xml",
        "//base:plugin_xml",
        "//cpp:plugin_xml",
        "//dart:plugin_xml",
        "//javascript:plugin_xml",
        "//python:plugin_xml",
        "//skylark:plugin_xml",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "src/META-INF/clwb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    plugin_id = "com.google.idea.bazel.clwb",
    plugin_name = "Bazel",
    plugin_xml = ":merged_plugin_xml",
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

java_library(
    name = "clwb_lib",
    srcs = glob(["src/**/*.java"]),
    runtime_deps = [
        "//dart",
        "//javascript",
        "//python",
        "//skylark",
        "//terminal",
    ],
    deps = [
        "//base",
        "//common/actions",
        "//common/experiments",
        "//cpp",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//sdkcompat",
    ],
)

OPTIONAL_PLUGIN_XMLS = [
    "//python:optional_xml",
    "//javascript:optional_xml",
    "//terminal:optional_xml",
]

intellij_plugin(
    name = "clwb_bazel",
    optional_plugin_xmls = OPTIONAL_PLUGIN_XMLS,
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":clwb_lib",
    ],
)

repackaged_files(
    name = "plugin_jar",
    srcs = [":clwb_bazel"],
    prefix = "clwb/lib",
)

repackaged_files(
    name = "aspect_directory",
    srcs = ["//aspect:aspect_files"],
    prefix = "clwb/aspect",
    strip_prefix = "/aspect",
)

repackaged_files(
    name = "clwb_bazel_gdbserver_wrapper",
    srcs = ["//clwb:gdbserver_wrapper"],
    prefix = "clwb/gdb",
    strip_prefix = "/clwb",
)

intellij_plugin_debug_target(
    name = "clwb_bazel_dev",
    deps = [
        ":aspect_directory",
        ":clwb_bazel_gdbserver_wrapper",
        ":plugin_jar",
    ],
)

plugin_deploy_zip(
    name = "clwb_bazel_zip",
    srcs = [
        ":aspect_directory",
        ":clwb_bazel_gdbserver_wrapper",
        ":plugin_jar",
    ],
    zip_filename = "clwb_bazel.zip",
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.clwb",
    deps = [
        ":clwb_lib",
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
    ],
)

filegroup(
    name = "gdbserver_wrapper",
    srcs = ["gdbserver"],
)
