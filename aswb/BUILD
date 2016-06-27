#
# Description: Builds ASwB for blaze and bazel
#

load(
    "//build_defs:build_defs.bzl",
    "merged_plugin_xml",
    "stamped_plugin_xml",
    "intellij_plugin",
)

merged_plugin_xml(
    name = "merged_plugin_xml_common",
    srcs = [
        "src/META-INF/aswb.xml",
        "//blaze-base:plugin_xml",
        "//blaze-cpp:plugin_xml",
        "//blaze-java:plugin_xml",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml_bazel",
    srcs = [
        "src/META-INF/aswb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml_bazel",
    include_product_code_in_stamp = True,
    plugin_xml = ":merged_plugin_xml_bazel",
    stamp_since_build = True,
    version_file = "//:version",
)

java_library(
    name = "aswb_lib",
    srcs = glob(["src/**/*.java"]),
    resources = glob(["resources/**/*"]),
    visibility = ["//visibility:public"],
    deps = [
        "//blaze-base",
        "//blaze-base:proto-deps",
        "//blaze-cpp",
        "//blaze-java",
        "//intellij-platform-sdk:bundled_plugins",
        "//intellij-platform-sdk:plugin_api",
        "//third_party:jsr305",
    ],
)

load(
    "//intellij_test:test_defs.bzl",
    "intellij_test",
)

intellij_test(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.android",
    deps = [
        ":aswb_lib",
        "//blaze-base",
        "//blaze-base:proto-deps",
        "//blaze-base:unit_test_utils",
        "//blaze-java",
        "//intellij-platform-sdk:bundled_plugins_for_tests",
        "//intellij-platform-sdk:plugin_api_for_tests",
        "//intellij_test:lib",
        "//third_party:jsr305",
        "//third_party:test_lib",
    ],
)

intellij_plugin(
    name = "aswb_bazel",
    plugin_xml = ":stamped_plugin_xml_bazel",
    deps = [
        ":aswb_lib",
    ],
)
