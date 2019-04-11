#
# Description: A Skylark debugging client for IntelliJ.
#

licenses(["notice"])  # Apache 2.0

load("//build_defs:build_defs.bzl", "intellij_plugin_library")

java_library(
    name = "skylark",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//sdkcompat",
        "//third_party/auto_value",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/blaze-skylark.xml"],
    visibility = ["//visibility:public"],
    deps = [":skylark"],
)
