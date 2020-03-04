load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "scala_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2019.2": ["@scala_2019_2//:scala"],
        "intellij-ue-2019.2": ["@scala_2019_2//:scala"],
        "intellij-2019.3": ["@scala_2019_3//:scala"],
        "intellij-ue-2019.3": ["@scala_2019_3//:scala"],
        "intellij-2020.1": ["@scala_2020_1//:scala"],
        "intellij-ue-2020.1": ["@scala_2020_1//:scala"],
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
