load("//build_defs:build_defs.bzl", "intellij_plugin_library")

intellij_plugin_library(
    name = "debugger",
    srcs = glob(["**/*.java"]),
    plugin_xmls = ["library.xml"],
    visibility = ["//skylark:__pkg__"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//proto:proto_deps",
    ],
)
