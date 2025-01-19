load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    exports = select_for_plugin_api({
        "intellij-ue-2025.1": ["@go_2025_1//:go"],
        "intellij-2025.1": ["@go_2025_1//:go"],
        "intellij-2024.2": ["@go_2024_2//:go"],
        "intellij-ue-2024.2": ["@go_2024_2//:go"],
        "intellij-2024.3": ["@go_2024_3//:go"],
        "intellij-ue-2024.3": ["@go_2024_3//:go"],
        "default": [],
    }),
    name = "go_internal",
    visibility = ["//visibility:private"],
)

java_library(
    exports = [":go_internal"],
    name = "go_for_tests",
    testonly = 1,
)

java_library(
    exports = [":go_internal"],
    name = "go",
    neverlink = 1,
)
