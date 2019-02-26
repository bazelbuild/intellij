licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "merged_plugin_xml",
    "stamped_plugin_xml",
    "intellij_plugin",
    "optional_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
    "intellij_integration_test_suite",
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-scala.xml"],
    visibility = ["//visibility:public"],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.intellij.scala",
    plugin_xml = "src/META-INF/scala-contents.xml",
    visibility = ["//visibility:public"],
)

OPTIONAL_PLUGIN_XMLS = [
    ":optional_xml",
    "//java:optional_java",
]

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        ":plugin_xml",
        "//base:plugin_xml",
        "//java:plugin_xml",
    ],
)

stamped_plugin_xml(
    name = "scala_plugin_xml",
    plugin_id = "com.google.idea.blaze.scala",
    plugin_name = "com.google.idea.blaze.scala",
    plugin_xml = "merged_plugin_xml",
)

java_library(
    name = "scala",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "//third_party/scala",
    ],
)

intellij_plugin(
    name = "scala_integration_test_plugin",
    testonly = 1,
    optional_plugin_xmls = OPTIONAL_PLUGIN_XMLS,
    plugin_xml = ":scala_plugin_xml",
    deps = [":scala"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze",
    deps = [
        ":scala",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//third_party/scala:scala_for_tests",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "org.intellij.scala,com.google.idea.blaze.scala",
    test_package_root = "com.google.idea.blaze.scala",
    runtime_deps = [":scala_integration_test_plugin"],
    deps = [
        ":scala",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//third_party/scala:scala_for_tests",
        "@junit//jar",
    ],
)
