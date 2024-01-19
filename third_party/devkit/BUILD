load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "devkit_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2024.1": ["@devkit_2024_1//:devkit"],
        "intellij-ue-2024.1": ["@devkit_2024_1//:devkit"],
        "default": [],
    }),
)

java_library(
    name = "devkit",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = [":devkit_internal"],
)

java_library(
    name = "devkit_for_tests",
    testonly = 1,
    visibility = ["//plugin_dev:__pkg__"],
    exports = [":devkit_internal"],
)
