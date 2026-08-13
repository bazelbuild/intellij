load("//build_defs:build_defs.bzl", "intellij_plugin_library", "optional_plugin_xml")

optional_plugin_xml(
    name = "catch_plugin_xml",
    module = [
        "org.jetbrains.plugins.clion.radler",
        "org.jetbrains.plugins.clion.test.catch",
    ],
    plugin_xml = "catch-test-plugin.xml",
)

intellij_plugin_library(
    name = "catch",
    srcs = ["CatchTestSupport.kt"],
    optional_plugin_xmls = [":catch_plugin_xml"],
    visibility = ["//clwb/src/com/google/idea/blaze/clwb/radler:__pkg__"],
    deps = [
        "//clwb:clwb_lib",
        "//clwb/src/com/google/idea/blaze/clwb/radler:lib",
    ],
)

optional_plugin_xml(
    name = "gtest_plugin_xml",
    module = [
        "org.jetbrains.plugins.clion.radler",
        "org.jetbrains.plugins.clion.test.google",
    ],
    plugin_xml = "gtest-test-plugin.xml",
)

intellij_plugin_library(
    name = "gtest",
    srcs = ["GoogleTestSupport.kt"],
    optional_plugin_xmls = [":gtest_plugin_xml"],
    visibility = ["//clwb/src/com/google/idea/blaze/clwb/radler:__pkg__"],
    deps = [
        "//clwb:clwb_lib",
        "//clwb/src/com/google/idea/blaze/clwb/radler:lib",
    ],
)
