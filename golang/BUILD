load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "golang",
    srcs = glob(["src/**/*.java"]),
    visibility = [
        "//java/com/google/devtools/intellij/blaze/plugin/golang:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/golang:__pkg__",
    ],
    deps = [
        "//base",
        "//common/experiments",
        "//common/util:transactions",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//sdkcompat",
        "//third_party/go",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.jetbrains.plugins.go",
    plugin_xml = "src/META-INF/go-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    plugin_xmls = ["src/META-INF/blaze-go.xml"],
    visibility = ["//visibility:public"],
    deps = [":golang"],
)

stamped_plugin_xml(
    name = "golang_plugin_xml",
    plugin_id = "com.google.idea.blaze.golang",
    plugin_name = "com.google.idea.blaze.golang",
)

intellij_plugin(
    name = "golang_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":golang_plugin_xml",
    deps = [
        ":plugin_library",
        "//base:plugin_library",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.golang",
    deps = [
        ":golang",
        "//base",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//sdkcompat",
        "//third_party/go:go_for_tests",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.golang,org.jetbrains.plugins.go",
    test_package_root = "com.google.idea.blaze.golang",
    runtime_deps = [
        ":golang_integration_test_plugin",
    ],
    deps = [
        ":golang",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//third_party/go:go_for_tests",
        "@junit//jar",
    ],
)
