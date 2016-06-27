#
# Description: Builds ijwb
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
        "src/META-INF/ijwb.xml",
        "//blaze-base:plugin_xml",
        "//blaze-java:plugin_xml",
        "//blaze-plugin-dev:plugin_xml",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml_bazel",
    srcs = [
        "src/META-INF/ijwb_bazel.xml",
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
    name = "ijwb_lib",
    srcs = glob(["src/**/*.java"]),
    exports = [
        "//blaze-plugin-dev",
    ],
    deps = [
        "//blaze-base",
        "//blaze-java",
        "//intellij-platform-sdk:plugin_api",
        "//third_party:jsr305",
    ],
)

intellij_plugin(
    name = "ijwb_bazel",
    plugin_xml = ":stamped_plugin_xml_bazel",
    deps = [
        ":ijwb_lib",
    ],
)
