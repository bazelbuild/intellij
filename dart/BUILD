load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load("//build_defs:build_defs.bzl", "intellij_plugin_library")
load("//testing:test_defs.bzl", "intellij_unit_test_suite")

java_library(
    name = "dart",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/blaze-dart.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":dart"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.dart",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":dart",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@com_google_guava_guava//jar",
        "@junit//jar",
    ],
)
