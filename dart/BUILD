licenses(["notice"])  # Apache 2.0

load("//testing:test_defs.bzl", "intellij_unit_test_suite")

java_library(
    name = "dart",
    srcs = glob(["src/**/*.java"]),
    resources = glob(["resources/**/*"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-dart.xml"],
    visibility = ["//visibility:public"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.dart",
    deps = [
        ":dart",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
    ],
)
