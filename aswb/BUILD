#
# Description: Builds ASwB for blaze and bazel
#

licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
    "stamped_plugin_xml",
)
load("//:version.bzl", "VERSION")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

merged_plugin_xml(
    name = "merged_plugin_xml_common",
    srcs = [
        "src/META-INF/aswb.xml",
        "//base:plugin_xml",
        "//cpp:plugin_xml",
        "//java:plugin_xml",
    ] + select_for_plugin_api({
        # TODO(chaorenl): remove when 2.2 is obsolete
        "android-studio-145.1617.8": [],
        "android-studio-2.3.0.3": ["2.3/src/META-INF/aswb_beta.xml"],
        "android-studio-2.3.0.4": ["2.3/src/META-INF/aswb_beta.xml"],
        "android-studio-2.3.0.6": ["2.3/src/META-INF/aswb_beta.xml"],
    }),
    visibility = [
        "//visibility:public",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "src/META-INF/aswb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    include_product_code_in_stamp = True,
    plugin_id = "com.google.idea.bazel.aswb",
    plugin_name = "Android Studio with Bazel",
    plugin_xml = ":merged_plugin_xml",
    stamp_since_build = True,
    version = VERSION,
)

java_library(
    name = "aswb_lib",
    srcs = glob(["src/**/*.java"]) + select_for_plugin_api({
        # TODO(chaorenl): remove when 2.2 is obsolete
        "android-studio-145.1617.8": glob(["2.2/src/**/*.java"]),
        "android-studio-2.3.0.3": glob(["2.3/src/**/*.java"]),
        "android-studio-2.3.0.4": glob(["2.3/src/**/*.java"]),
        "android-studio-2.3.0.6": glob(["2.3/src/**/*.java"]),
    }),
    resources = glob(["resources/**/*"]),
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//base",
        "//common/experiments",
        "//cpp",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
    ],
)

# TODO(chaorenl): remove when 2.2 is obsolete
java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = select_for_plugin_api({
        "android-studio-145.1617.8": glob(["2.2/tests/utils/integration/**/*.java"]),
        "android-studio-2.3.0.3": glob(["2.3/tests/utils/integration/**/*.java"]),
        "android-studio-2.3.0.4": glob(["2.3/tests/utils/integration/**/*.java"]),
        "android-studio-2.3.0.6": glob(["2.3/tests/utils/integration/**/*.java"]),
    }),
    deps = [
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]) + select_for_plugin_api({
        # TODO(chaorenl): remove when 2.2 is obsolete
        "android-studio-145.1617.8": [],
        "android-studio-2.3.0.3": glob(["2.3/tests/unittests/**/*.java"]),
        "android-studio-2.3.0.4": glob(["2.3/tests/unittests/**/*.java"]),
        "android-studio-2.3.0.6": glob(["2.3/tests/unittests/**/*.java"]),
    }),
    test_package_root = "com.google.idea.blaze.android",
    deps = [
        ":aswb_lib",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]) + select_for_plugin_api({
        # TODO(chaorenl): remove when 2.2 is obsolete
        "android-studio-145.1617.8": [],
        "android-studio-2.3.0.3": glob(["2.3/tests/integrationtests/**/*.java"]),
        "android-studio-2.3.0.4": glob(["2.3/tests/integrationtests/**/*.java"]),
        "android-studio-2.3.0.6": glob(["2.3/tests/integrationtests/**/*.java"]),
    }),
    platform_prefix = "AndroidStudio",
    required_plugins = "com.google.idea.bazel.aswb",
    test_package_root = "com.google.idea.blaze.android",
    runtime_deps = [
        ":aswb_bazel",
    ],
    deps = [
        ":aswb_lib",
        ":integration_test_utils",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//java",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)

intellij_plugin(
    name = "aswb_bazel",
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":aswb_lib",
    ],
)
