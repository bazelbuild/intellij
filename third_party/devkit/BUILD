load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "devkit_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2023.3": ["@devkit_2023_3//:devkit"],
        "intellij-ue-2023.3": ["@devkit_2023_3//:devkit"],
        "default": [],
    }),
)

java_library(
    name = "devkit",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = [":devkit_internal"],
)
