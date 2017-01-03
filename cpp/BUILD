licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "cpp",
    srcs = glob(
        ["src/**/*.java"],
        exclude = [
            "src/com/google/idea/blaze/cpp/versioned/**",
        ],
    ) + select_for_plugin_api({
        "android-studio-145.1617.8": [":api_v145_sources"],
        "default": [":api_v162_sources"],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:plugin_api",
        "@jsr305_annotations//jar",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-cpp.xml"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "api_v145_sources",
    srcs = glob(["src/com/google/idea/blaze/cpp/versioned/v145/**"]),
    visibility = ["//visibility:private"],
)

filegroup(
    name = "api_v162_sources",
    srcs = glob(["src/com/google/idea/blaze/cpp/versioned/v162/**"]),
    visibility = ["//visibility:private"],
)

load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.cpp",
    deps = [
        ":cpp",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)
