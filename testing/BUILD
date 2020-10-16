# Description:
#
# Common test utilities for IntelliJ plugins.

load(
    "//intellij_platform_sdk:build_defs.bzl",
    "select_for_ide",
    "select_for_plugin_api",
)

package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache 2.0

java_library(
    name = "lib",
    testonly = 1,
    srcs = glob(["src/**/*.java"]) +
           select_for_ide(
               android_studio = glob([
                   "studio/**/*.java",
                   "cidr/**/*.java",
               ]),
               default = [],
           ) + select_for_plugin_api({
        "android-studio-4.1": ["testcompat/v201/com/google/idea/testing/cidr/StubOCResolveConfiguration.java"],
        "android-studio-4.2": ["testcompat/v201/com/google/idea/testing/cidr/StubOCResolveConfiguration.java"],
        "default": [],
    }),
    deps = [
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@junit//jar",
    ],
)
