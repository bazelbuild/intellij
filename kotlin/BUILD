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
    "intellij_unit_test_suite",
)

licenses(["notice"])

java_library(
    name = "kotlin",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//java/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:kotlin",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.jetbrains.kotlin",
    plugin_xml = "src/META-INF/kotlin-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    plugin_xmls = ["src/META-INF/blaze-kotlin.xml"],
    visibility = ["//visibility:public"],
    deps = [":kotlin"],
)

stamped_plugin_xml(
    name = "kotlin_plugin_xml",
    plugin_id = "com.google.idea.blaze.kotlin",
    plugin_name = "com.google.idea.blaze.kotlin",
)

intellij_plugin(
    name = "kotlin_integration_test_plugin",
    testonly = 1,
    optional_plugin_xmls = [":optional_xml"],
    plugin_xml = ":kotlin_plugin_xml",
    deps = [
        ":plugin_library",
        "//base:plugin_library",
        "//java:plugin_library",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze",
    deps = [
        ":kotlin",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:kotlin_for_tests",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "org.jetbrains.kotlin,com.google.idea.blaze.kotlin",
    test_package_root = "com.google.idea.blaze.kotlin",
    runtime_deps = [":kotlin_integration_test_plugin"],
    deps = [
        ":kotlin",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:kotlin_for_tests",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//proto:proto_deps",
        "//third_party/auto_value",
        "@junit//jar",
    ],
)
