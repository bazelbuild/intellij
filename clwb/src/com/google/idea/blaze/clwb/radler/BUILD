load("//build_defs:build_defs.bzl", "intellij_plugin_library", "optional_plugin_xml")

optional_plugin_xml(
    name = "plugin_xml",
    module = ["org.jetbrains.plugins.clion.radler"],
    plugin_xml = "optional-plugin.xml",
)

intellij_plugin_library(
    name = "lib",
    srcs = glob(["*.kt"]),
    optional_plugin_xmls = [":plugin_xml"],
    visibility = ["//clwb/src/com/google/idea/blaze/clwb/radler:__subpackages__"],
    deps = ["//clwb:clwb_lib"],
)

intellij_plugin_library(
    name = "radler",
    visibility = ["//clwb:__subpackages__"],
    deps = [
        ":lib",
        "//clwb/src/com/google/idea/blaze/clwb/radler/test:catch",
        "//clwb/src/com/google/idea/blaze/clwb/radler/test:gtest",
    ],
)
