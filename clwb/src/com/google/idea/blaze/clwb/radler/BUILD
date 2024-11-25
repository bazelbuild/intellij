load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("//build_defs:build_defs.bzl", "optional_plugin_xml")

licenses(["notice"])

package(default_visibility = ["//clwb:__subpackages__"])

optional_plugin_xml(
    name = "plugin_xml",
    module = ["org.jetbrains.plugins.clion.radler"],
    plugin_xml = "plugin.xml",
)

kt_jvm_library(
    name = "lib",
    srcs = glob(["*.kt"]),
    deps = [
        "//intellij_platform_sdk:plugin_api",
        "//clwb:clwb_lib",
    ],
)
