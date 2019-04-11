licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin_library",
    "optional_plugin_xml",
)

java_library(
    name = "terminal",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:terminal",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.jetbrains.plugins.terminal",
    plugin_xml = "src/META-INF/terminal-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    visibility = ["//visibility:public"],
    deps = [":terminal"],
)
