load("//build_defs:build_defs.bzl", "intellij_plugin_library", "optional_plugin_xml")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_version")

optional_plugin_xml(
    name = "plugin_xml",
    module = ["com.intellij.cidr.lang"],
    plugin_xml = "optional-plugin.xml",
)

intellij_plugin_library(
    name = "lib",
    srcs = glob(["**/*.java"]),
    optional_plugin_xmls = [":plugin_xml"],
    deps = ["//clwb:clwb_lib"],
)

intellij_plugin_library(
    name = "oclang",
    visibility = ["//clwb:__subpackages__"],
    deps = select_for_version({
        "2025.3": [":lib"],
        "2026.1": [":lib"],
    }),
)
