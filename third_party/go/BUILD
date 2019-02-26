package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "go_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2018.3": ["@go_2018_3//:go"],
        "intellij-ue-2018.3": ["@go_2018_3//:go"],
        "default": [],
    }),
)

java_library(
    name = "go_for_tests",
    testonly = 1,
    exports = [":go_internal"],
)

java_library(
    name = "go",
    neverlink = 1,
    exports = [":go_internal"],
)
