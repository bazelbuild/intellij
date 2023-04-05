#
# Description: Builds clwb
#

load("//:version.bzl", "VERSION")
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
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
load(
    "//:build-visibility.bzl",
    "CLWB_PACKAGES_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])

optional_plugin_xml(
    name = "optional_bundled_boost_plugin",
    module = "org.jetbrains.plugins.clion.test.boost",
    plugin_xml = "src/META-INF/clion-test-boost.xml",
)

optional_plugin_xml(
    name = "optional_bundled_catch_plugin",
    module = "org.jetbrains.plugins.clion.test.catch",
    plugin_xml = "src/META-INF/clion-test-catch.xml",
)

optional_plugin_xml(
    name = "optional_bundled_gtest_plugin",
    module = "org.jetbrains.plugins.clion.test.google",
    plugin_xml = "src/META-INF/clion-test-google.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/clwb.xml"],
    optional_plugin_xmls = [
        ":optional_bundled_boost_plugin",
        ":optional_bundled_catch_plugin",
        ":optional_bundled_gtest_plugin",
        ],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":clwb_lib"],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    description_file = "src/META-INF/description.html",
    plugin_id = "com.google.idea.bazel.clwb",
    plugin_name = "Bazel for CLion",
    # #api212: We depend on an API which is only contained in 2021.2.1+.
    since_build_numbers = {"212": "212.5080.55"},
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

java_library(
    name = "clwb_lib",
    srcs = glob(["src/**/*.java"]),
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

intellij_plugin(
    name = "clwb_bazel",
    plugin_xml = ":stamped_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//base:plugin_library",
        "//cpp:plugin_library",
        "//dart:plugin_library",
        "//javascript:plugin_library",
        "//python:plugin_library",
        "//skylark:plugin_library",
        "//terminal:plugin_library",
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
    visibility = CLWB_PACKAGES_VISIBILITY,
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
    visibility = ["//visibility:public"],
)
