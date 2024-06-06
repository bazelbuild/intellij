load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "scala_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2021.3": ["@scala_2021_3//:scala"],
        "intellij-ue-2021.3": ["@scala_2021_3//:scala"],
        "intellij-2022.1": ["@scala_2022_1//:scala"],
        "intellij-ue-2022.1": ["@scala_2022_1//:scala"],
        "intellij-2022.2": ["@scala_2022_2//:scala"],
        "intellij-ue-2022.2": ["@scala_2022_2//:scala"],
        "intellij-2022.3": ["@scala_2022_3//:scala"],
        "intellij-ue-2022.3": ["@scala_2022_3//:scala"],
        "intellij-2023.1": ["@scala_2023_1//:scala"],
        "intellij-ue-2023.1": ["@scala_2023_1//:scala"],
        "intellij-2023.2": ["@scala_2023_2//:scala"],
        "intellij-ue-2023.2": ["@scala_2023_2//:scala"],
        "intellij-2023.3": ["@scala_2023_3//:scala"],
        "intellij-ue-2023.3": ["@scala_2023_3//:scala"],
        "intellij-2024.1": ["@scala_2024_1//:scala"],
        "intellij-ue-2024.1": ["@scala_2024_1//:scala"],
        "intellij-2024.2": ["@scala_2024_2//:scala"],
        "intellij-ue-2024.2": ["@scala_2024_2//:scala"],
        "default": [],
    }),
)

java_library(
    name = "scala_for_tests",
    testonly = 1,
    exports = [":scala_internal"],
)

java_library(
    name = "scala",
    neverlink = 1,
    exports = [":scala_internal"],
)
