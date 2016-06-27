package(default_visibility = ["//visibility:public"])

java_library(
    name = "blaze-base",
    srcs = glob(["src/**/*.java"]),
    resources = glob(["resources/**/*"]),
    deps = [
        ":proto-deps",
        "//intellij-platform-sdk:plugin_api",
        "//third_party:jsr305",
        "//third_party:trickle",
    ],
)

java_import(
    name = "proto-deps",
    jars = ["lib/proto_deps.jar"],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-base.xml"],
)

java_library(
    name = "unit_test_utils",
    srcs = glob(["tests/utils/unit/**/*.java"]),
    deps = [
        ":blaze-base",
        "//intellij-platform-sdk:plugin_api_for_tests",
        "//third_party:jsr305",
        "//third_party:test_lib",
    ],
)

java_library(
    name = "integration_test_utils",
    srcs = glob(["tests/utils/integration/**/*.java"]),
    deps = [
        ":blaze-base",
        ":proto-deps",
        ":unit_test_utils",
        "//intellij-platform-sdk:plugin_api_for_tests",
        "//third_party:jsr305",
        "//third_party:test_lib",
    ],
)

load(
    "//intellij_test:test_defs.bzl",
    "intellij_test",
)

intellij_test(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.base",
    deps = [
        ":blaze-base",
        ":proto-deps",
        ":unit_test_utils",
        "//intellij-platform-sdk:plugin_api_for_tests",
        "//intellij_test:lib",
        "//third_party:jsr305",
        "//third_party:test_lib",
    ],
)

intellij_test(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    integration_tests = True,
    required_plugins = "com.google.idea.blaze.ijwb",
    test_package_root = "com.google.idea.blaze.base",
    deps = [
        ":blaze-base",
        ":integration_test_utils",
        ":unit_test_utils",
        "//ijwb:ijwb_bazel",
        "//intellij-platform-sdk:plugin_api_for_tests",
        "//intellij_test:lib",
        "//third_party:jsr305",
        "//third_party:test_lib",
    ],
)
