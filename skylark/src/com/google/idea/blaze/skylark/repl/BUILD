load("//build_defs:build_defs.bzl", "intellij_plugin_library")

intellij_plugin_library(
    name = "repl",
    srcs = glob(["*.kt"]),
    plugin_xmls = ["plugin.xml"],
    visibility = ["//skylark:__subpackages__"],
    deps = [
        "//base",
        "//common/util:datafiles",
        "//common/util:runjar",
        "//third_party/bazel/jars:starlark_repl",
    ],
)
