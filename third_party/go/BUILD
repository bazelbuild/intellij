load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "go_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2020.2": ["@go_2020_2//:go"],
        "intellij-ue-2020.2": ["@go_2020_2//:go"],
        "intellij-2020.3": ["@go_2020_3//:go"],
        "intellij-ue-2020.3": ["@go_2020_3//:go"],
        "intellij-2021.1": ["@go_2021_1//:go"],
        "intellij-ue-2021.1": ["@go_2021_1//:go"],
        "intellij-2021.2": ["@go_2021_2//:go"],
        "intellij-ue-2021.2": ["@go_2021_2//:go"],
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
