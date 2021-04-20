#
# Description: A Skylark debugging client for IntelliJ.
#

load("//build_defs:build_defs.bzl", "intellij_plugin_library")

licenses(["notice"])  # Apache 2.0

java_library(
    name = "skylark",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//java/com/google/devtools/intellij/blaze/plugin/skylark:__pkg__"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/blaze-skylark.xml"],
    visibility = ["//visibility:public"],
    deps = [":skylark"],
)
