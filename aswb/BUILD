#
# Description: Builds ASwB for blaze and bazel
#

licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
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
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

merged_plugin_xml(
    name = "merged_plugin_xml_common",
    srcs = [
        "src/META-INF/aswb.xml",
        "//base:plugin_xml",
        "//dart:plugin_xml",
        "//java:plugin_xml",
        "//kotlin:plugin_xml",
        "//python:plugin_xml",
        "//skylark:plugin_xml",
    ] + select_for_plugin_api({
        "android-studio-3.2": ["src/META-INF/aswb_3_2.xml"],
        "android-studio-3.3": ["src/META-INF/aswb_3_3.xml"],
        "android-studio-3.4": ["src/META-INF/aswb_3_3.xml"],
    }),
)

optional_plugin_xml(
    name = "optional_cpp_xml",
    module = "com.intellij.modules.cidr.lang",
    plugin_xml = "//cpp:plugin_xml",
)

optional_plugin_xml(
    name = "optional_ndk_xml",
    module = "com.android.tools.ndk",
    plugin_xml = "src/META-INF/ndk_contents.xml",
)

OPTIONAL_PLUGIN_XMLS = [
    ":optional_cpp_xml",
    ":optional_ndk_xml",
    "//java:optional_java",
    "//java:optional_coverage",
    "//kotlin:optional_xml",
    "//python:optional_xml",
]

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "src/META-INF/aswb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    plugin_id = "com.google.idea.bazel.aswb",
    plugin_name = "Bazel",
    plugin_xml = ":merged_plugin_xml",
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

java_library(
    name = "aswb_lib",
    srcs = glob(["src/**/*.java"]) + select_for_plugin_api({
        "android-studio-3.2": glob(["src_3_2/**/*.java"]),
        "android-studio-3.3": glob(["src_3_3/**/*.java"]),
        "android-studio-3.4": glob(["src_3_4/**/*.java"]),
    }),
    resources = glob(["resources/**/*"]),
    runtime_deps = [
        "//dart",
        "//kotlin",
        "//python",
        "//skylark",
    ],
    deps = [
        "//base",
        "//common/concurrency",
        "//common/experiments",
        "//cpp",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "//sdkcompat",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]) + select_for_plugin_api({
        "android-studio-3.2": glob(["tests/unittests_3_2/**/*.java"]),
        "android-studio-3.3": glob(["tests/unittests_3_3/**/*.java"]),
        "android-studio-3.4": glob(["tests/unittests_3_3/**/*.java"]),
    }),
    test_package_root = "com.google.idea.blaze.android",
    deps = [
        ":aswb_lib",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "//proto:proto_deps",
        "@junit//jar",
    ],
)

test_suite(
    name = "integration_tests",
    tests = [
        ":NdkDependenciesTest",
        ":normal_integration_tests",
    ],
)

intellij_integration_test_suite(
    name = "normal_integration_tests",
    srcs = glob(
        ["tests/integrationtests/**/*.java"],
        exclude = ["tests/integrationtests/com/google/idea/blaze/android/plugin/NdkDependenciesTest.java"],
    ) + select_for_plugin_api({
        "android-studio-3.2": glob(["tests/integrationtests_3_2/**/*.java"]),
        "android-studio-3.3": glob(["tests/integrationtests_3_3/**/*.java"]),
        "android-studio-3.4": glob(["tests/integrationtests_3_4/**/*.java"]),
    }),
    platform_prefix = "AndroidStudio",
    required_plugins = "com.google.idea.bazel.aswb",
    test_package_root = "com.google.idea.blaze.android",
    runtime_deps = [
        ":aswb_bazel",
    ],
    deps = [
        ":aswb_lib",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//cpp",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "//proto:proto_deps",
        "//sdkcompat",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "NdkDependenciesTest",
    srcs = ["tests/integrationtests/com/google/idea/blaze/android/plugin/NdkDependenciesTest.java"],
    platform_prefix = "AndroidStudio",
    required_plugins = "com.google.idea.bazel.aswb",
    test_package_root = "com.google.idea.blaze.android",
    runtime_deps = [
        ":aswb_bazel",
        "//cpp",
        "//java",
    ],
    deps = [
        ":aswb_lib",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//proto:proto_deps",
        "@junit//jar",
    ],
)

intellij_plugin(
    name = "aswb_bazel",
    optional_plugin_xmls = OPTIONAL_PLUGIN_XMLS,
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":aswb_lib",
    ],
)

repackaged_files(
    name = "plugin_jar",
    srcs = [":aswb_bazel"],
    prefix = "aswb/lib",
)

repackaged_files(
    name = "aspect_directory",
    srcs = ["//aspect:aspect_files"],
    prefix = "aswb/aspect",
    strip_prefix = "/aspect",
)

intellij_plugin_debug_target(
    name = "aswb_bazel_dev",
    deps = [
        ":aspect_directory",
        ":plugin_jar",
    ],
)

plugin_deploy_zip(
    name = "aswb_bazel_zip",
    srcs = [
        ":aspect_directory",
        ":plugin_jar",
    ],
    zip_filename = "aswb_bazel.zip",
)
