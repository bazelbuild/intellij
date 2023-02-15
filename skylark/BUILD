#
# Description: A Skylark debugging client for IntelliJ.
#

load("//build_defs:build_defs.bzl", "intellij_plugin_library")
load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
    "SKYLARK_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "skylark",
    srcs = glob(["src/**/*.java"]),
    visibility = SKYLARK_PACKAGES_VISIBILITY,
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
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":skylark"],
)
