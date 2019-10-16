package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "go_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2019.1": ["@go_2019_1//:go"],
        "intellij-ue-2019.1": ["@go_2019_1//:go"],
        "intellij-2019.2": ["@go_2019_2//:go"],
        "intellij-ue-2019.2": ["@go_2019_2//:go"],
        "intellij-2019.3": ["@go_2019_3//:go"],
        "intellij-ue-2019.3": ["@go_2019_3//:go"],
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
