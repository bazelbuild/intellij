load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "go_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2021.3": ["@go_2021_3//:go"],
        "intellij-ue-2021.3": ["@go_2021_3//:go"],
        "intellij-2022.1": ["@go_2022_1//:go"],
        "intellij-ue-2022.1": ["@go_2022_1//:go"],
        "intellij-2022.2": ["@go_2022_2//:go"],
        "intellij-ue-2022.2": ["@go_2022_2//:go"],
        "intellij-2022.3": ["@go_2022_3//:go"],
        "intellij-ue-2022.3": ["@go_2022_3//:go"],
        "intellij-2023.1": ["@go_2023_1//:go"],
        "intellij-ue-2023.1": ["@go_2023_1//:go"],
        "intellij-2023.2": ["@go_2023_2//:go"],
        "intellij-ue-2023.2": ["@go_2023_2//:go"],
        "intellij-2023.3": ["@go_2023_3//:go"],
        "intellij-ue-2023.3": ["@go_2023_3//:go"],	
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
