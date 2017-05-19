#
# Description: Builds clwb
#

licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
    "stamped_plugin_xml",
)
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")
load("//:version.bzl", "VERSION")

merged_plugin_xml(
    name = "merged_plugin_xml_common",
    srcs = [
        "src/META-INF/clwb.xml",
        "//base:plugin_xml",
        "//cpp:plugin_xml",
        "//python:plugin_xml",
    ],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "src/META-INF/clwb_bazel.xml",
        ":merged_plugin_xml_common",
    ],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    include_product_code_in_stamp = True,
    plugin_id = "com.google.idea.bazel.clwb",
    plugin_name = "CLion with Bazel",
    plugin_xml = ":merged_plugin_xml",
    stamp_since_build = True,
    version = VERSION,
)

java_library(
    name = "clwb_lib",
    srcs = glob(["src/**/*.java"]),
    runtime_deps = [
        "//terminal",
    ] + select_for_plugin_api({
        "clion-2016.3.2": [],
        "default": ["//python"],
    }),
    deps = [
        "//base",
        "//common/experiments",
        "//cpp",
        "//intellij_platform_sdk:plugin_api",
        "//sdkcompat",
        "@jsr305_annotations//jar",
    ],
)

OPTIONAL_PLUGIN_XMLS = [
    "//python:optional_xml",
    "//terminal:optional_xml",
]

intellij_plugin(
    name = "clwb_bazel",
    optional_plugin_xmls = OPTIONAL_PLUGIN_XMLS,
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":clwb_lib",
    ],
)
