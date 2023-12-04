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
load(
    "//:build-visibility.bzl",
    "JAVASCRIPT_PACKAGES_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "javascript",
    srcs = glob(["src/**/*.java"]),
    visibility = JAVASCRIPT_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//sdkcompat",
        "//third_party/javascript",
        "@gson//jar",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "JavaScript",
    plugin_xml = "src/META-INF/javascript-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    plugin_xmls = ["src/META-INF/blaze-javascript.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":javascript"],
)

stamped_plugin_xml(
    name = "javascript_plugin_xml",
    plugin_id = "com.google.idea.blaze.javascript",
    plugin_name = "com.google.idea.blaze.javascript",
    plugin_xml = "tests/integrationtests/META-INF/test.xml",
)

intellij_plugin(
    name = "javascript_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":javascript_plugin_xml",
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
        ":javascript",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@com_google_guava_guava//jar",
        "@junit_junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.javascript,JavaScript",
    test_package_root = "com.google.idea.blaze",
    runtime_deps = [":javascript_integration_test_plugin"],
    deps = [
        ":javascript",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//third_party/javascript:javascript_for_tests",
        "@com_google_guava_guava//jar",
        "@junit_junit//jar",
    ],
)
