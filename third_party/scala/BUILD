package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "scala_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2018.1": ["@scala_2018_1//:scala"],
        "intellij-ue-2018.1": ["@scala_2018_1//:scala"],
        "intellij-2018.2": ["@scala_2018_2//:scala"],
        "intellij-ue-2018.2": ["@scala_2018_2//:scala"],
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
