#
# Description: Builds ijwb
#

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "plugin_deploy_zip",
    "repackaged_files",
    "stamped_plugin_xml",
)
load(
    "//build_defs:intellij_plugin_debug_target.bzl",
    "intellij_plugin_debug_target",
)
load("//:version.bzl", "VERSION")
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")
load(
    "//:build-visibility.bzl",
    "IJWB_PACKAGES_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load("//base/tests/utils/heavy:defs.bzl", "ijwb_integration_test")

licenses(["notice"])

optional_plugin_xml(
    name = "optional_java",
    module = ["com.intellij.java"],
    plugin_xml = "src/META-INF/java-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_java"],
    plugin_xmls = ["src/META-INF/ijwb.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":ijwb_lib"],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    description_file = "src/META-INF/description.html",
    plugin_id = "com.google.idea.bazel.ijwb",
    plugin_name = "Bazel for IntelliJ",
    # #api212: We depend on an API which is only contained in 2021.2.1+.
    since_build_numbers = {"212": "212.5080.55"},
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

java_library(
    name = "ijwb_lib",
    srcs = glob(["src/**/*.java"]),
    visibility = IJWB_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "//sdkcompat",
    ],
)

intellij_plugin(
    name = "ijwb_bazel",
    plugin_xml = ":stamped_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//base:plugin_library",
        "//dart:plugin_library",
        "//java:plugin_library",
        "//kotlin:plugin_library",
        "//plugin_dev:plugin_library",
        "//python:plugin_library",
        "//scala:plugin_library",
        "//skylark:plugin_library",
        "//terminal:plugin_library",
    ] + select_for_ide(
        intellij = [],
        intellij_ue = [
            "//gazelle:plugin_library",
            "//golang:plugin_library",
            "//javascript:plugin_library",
        ],
    ),
)

repackaged_files(
    name = "plugin_jar",
    srcs = [":ijwb_bazel"],
    prefix = "ijwb/lib",
)

repackaged_files(
    name = "aspect_directory",
    srcs = ["//aspect:aspect_files"],
    prefix = "ijwb/aspect",
    strip_prefix = "/aspect",
    visibility = IJWB_PACKAGES_VISIBILITY,
    java_copts = ["-source", "8", "-target", "8"],
)

repackaged_files(
    name = "aspect_template_directory",
    srcs = ["//aspect_template:aspect_files"],
    prefix = "ijwb/aspect_template",
    strip_prefix = "/aspect_template",
    visibility = IJWB_PACKAGES_VISIBILITY,
    java_copts = ["-source", "8", "-target", "8"],
)

repackaged_files(
    name = "fast_build_javac",
    srcs = ["//java:fast_build_javac"],
    prefix = "ijwb/lib",
    visibility = IJWB_PACKAGES_VISIBILITY,
    java_copts = ["-source", "8", "-target", "8"],
)

intellij_plugin_debug_target(
    name = "ijwb_bazel_dev",
    deps = [
        ":aspect_directory",
        ":aspect_template_directory",
        ":fast_build_javac",
        ":plugin_jar",
    ],
)

plugin_deploy_zip(
    name = "ijwb_bazel_zip",
    srcs = [
        ":aspect_directory",
        ":aspect_template_directory",
        ":fast_build_javac",
        ":plugin_jar",
    ],
    zip_filename = "ijwb_bazel.zip",
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.ijwb",
    deps = [
        ":ijwb_lib",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@com_google_guava_guava//jar",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "light_integration_tests",
    srcs = glob(["tests/light_integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.bazel.ijwb",
    test_package_root = "com.google.idea.blaze.ijwb",
    runtime_deps = [
        ":ijwb_bazel",
    ],
    deps = [
        ":ijwb_lib",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@org_opentest4j_opentest4j//jar",
    ],
)

java_library(
    name = "lib_heavy_integration_test",
    testonly = True,
    srcs = glob(["tests/heavy_integrationtests/com/google/idea/blaze/ijwb/base/*.java"]),
    deps = [
        "//base",
        "//base/tests/utils/heavy:lib",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        ":ijwb_lib"
    ]
)

ijwb_integration_test(
    name = "simple_integration_test",
    project = "simple",
    srcs = ["tests/heavy_integrationtests/com/google/idea/blaze/ijwb/SimpleTest.java"],
    deps = [":lib_heavy_integration_test"],
)

test_suite(
    name = "integration_tests",
    tests = [
        ":light_integration_tests",
        ":simple_integration_test",
    ],
)
