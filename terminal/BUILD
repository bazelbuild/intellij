licenses(["notice"])  # Apache 2.0

load("//build_defs:build_defs.bzl", "optional_plugin_xml")

java_library(
    name = "terminal",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:terminal",
        "@jsr305_annotations//jar",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.jetbrains.plugins.terminal",
    plugin_xml = "src/META-INF/terminal-contents.xml",
    visibility = ["//visibility:public"],
)
