load("//build_defs:build_defs.bzl", "intellij_plugin_library")

intellij_plugin_library(
    name = "execlog",
    srcs = glob(["*.kt"]),
    plugin_xmls = ["library.xml"],
    visibility = ["//base:__subpackages__"],
    deps = [
        "//base/src/com/google/idea/blaze/base/execlog/prototext",
        "//common/util:datafiles",
        "//common/util:runjar",
        "//third_party/bazel/jars:execlog_parser",
    ],
)
