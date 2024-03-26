load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)
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
    name = "scala",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "//sdkcompat",
        "//third_party/scala",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.intellij.scala",
    plugin_xml = "src/META-INF/scala-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    plugin_xmls = ["src/META-INF/blaze-scala.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":scala"],
)

stamped_plugin_xml(
    name = "scala_plugin_xml",
    plugin_id = "com.google.idea.blaze.scala",
    plugin_name = "com.google.idea.blaze.scala",
)

intellij_plugin(
    name = "scala_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":scala_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
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
        "@com_google_guava_guava//jar",
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
        "//intellij_platform_sdk:guava",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//third_party/scala:scala_for_tests",
        "@com_google_guava_guava//jar",
        "@junit//jar",
    ],
)
