load("//build_defs:build_defs.bzl", "intellij_plugin_library")

intellij_plugin_library(
    name = "execlog",
    srcs = glob(["*.kt"]),
    data = {
        "textmate/execlog/package.json": "textmate/package.json",
        "textmate/execlog/execlog.tmLanguage.json": "textmate/execlog.tmLanguage.json",
    },
    plugin_xmls = ["library.xml"],
    visibility = ["//base:__subpackages__"],
    deps = [
        "//common/util:datafiles",
        "//common/util:runjar",
        "//intellij_platform_sdk:textmate",
        "//third_party/bazel/jars:execlog_parser",
    ],
)
