load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
)
load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "plugin_dev",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//base",
        "//common/experiments",
        "//common/util:transactions",
        "//intellij_platform_sdk:devkit",  #api232
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "//shared",
        "//third_party/devkit",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "DevKit",
    plugin_xml = "src/META-INF/blaze-plugin-dev.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":plugin_dev"],
)

intellij_plugin_library(
    name = "empty",
)

stamped_plugin_xml(
    name = "plugin_dev_plugin_xml",
    plugin_id = "com.google.idea.blaze.plugin_dev",
    plugin_name = "com.google.idea.blaze.plugin_dev",
)

intellij_plugin(
    name = "plugin_dev_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":plugin_dev_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//base:plugin_library",
        "//java:plugin_library",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.plugin_dev,DevKit",
    test_package_root = "com.google.idea.blaze.plugin",
    runtime_deps = [
        ":plugin_dev_integration_test_plugin",
    ],
    deps = [
        ":plugin_dev",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//third_party/devkit:devkit_for_tests",
        "@junit//jar",
    ],
)
