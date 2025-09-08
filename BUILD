#
# Description: Blaze plugin for various IntelliJ products.
#
load(
    "//:build-visibility.bzl",
    "BAZEL_PLUGIN_SUBPACKAGES",
    "create_plugin_packages_group",
)

licenses(["notice"])

create_plugin_packages_group()

# Changelog file
filegroup(
    name = "changelog",
    srcs = ["CHANGELOG"],
    visibility = BAZEL_PLUGIN_SUBPACKAGES,
)

# CLwB tests, run with a CLion plugin SDK
test_suite(
    name = "clwb_tests",
    tests = [
        "//base:unit_tests",
        "//clwb:headless_tests",
        "//clwb:integration_tests",
        "//clwb:unit_tests",
        "//cpp:unit_tests",
        "//dart:unit_tests",
        "//python:unit_tests",
        "//skylark:unit_tests",
    ],
)
