load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
    "PYTHON_PACKAGES_VISIBILITY",
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

java_library(
    name = "python",
    srcs = glob(["src/**/*.java"]),
    visibility = PYTHON_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//common/experiments",
        "//common/util:transactions",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//querysync",
        "//sdkcompat",
        "//shared",
        "//third_party/python",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = ["com.intellij.modules.python"],
    plugin_xml = "src/META-INF/python-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    plugin_xmls = ["src/META-INF/blaze-python.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":python"],
)

stamped_plugin_xml(
    name = "python_plugin_xml",
    plugin_id = "com.google.idea.blaze.python",
    plugin_name = "com.google.idea.blaze.python",
)

intellij_plugin(
    name = "python_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":python_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//base:plugin_library",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.python",
    test_package_root = "com.google.idea.blaze.python",
    visibility = DEFAULT_TEST_VISIBILITY,
    runtime_deps = [
        ":python_integration_test_plugin",
    ],
    deps = [
        ":integration_test_utils",
        ":python",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//querysync",
        "//third_party/python:python_for_tests",
        "//third_party/toml:toml_for_tests",
        "@com_google_guava_guava//jar",
        "@junit//jar",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.python",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":python",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//proto:proto_deps",
        "//third_party/python:python_for_tests",
        "@com_google_guava_guava//jar",
        "@junit//jar",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/integration/**/*.java"]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//third_party/python:python_for_tests",
        "@com_google_guava_guava//jar",
        "@junit//jar",
    ],
)
