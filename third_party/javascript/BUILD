package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "javascript_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2018.1": ["@intellij_ue_2018_1//:javascript"],
        "intellij-ue-2018.1": ["@intellij_ue_2018_1//:javascript"],
        "intellij-2018.2": ["@intellij_ue_2018_2//:javascript"],
        "intellij-ue-2018.2": ["@intellij_ue_2018_2//:javascript"],
        "clion-2018.1": ["@clion_2018_1//:javascript"],
        "clion-2018.2": ["@clion_2018_2//:javascript"],
    }),
)

java_library(
    name = "css_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2018.1": ["@intellij_ue_2018_1//:css"],
        "intellij-ue-2018.1": ["@intellij_ue_2018_1//:css"],
        "intellij-2018.2": ["@intellij_ue_2018_2//:css"],
        "intellij-ue-2018.2": ["@intellij_ue_2018_2//:css"],
        "clion-2018.1": ["@clion_2018_1//:css"],
        "clion-2018.2": ["@clion_2018_2//:css"],
    }),
)

java_library(
    name = "javascript_for_tests",
    testonly = 1,
    exports = [
        ":css_internal",
        ":javascript_internal",
    ],
)

java_library(
    name = "javascript",
    neverlink = 1,
    exports = [":javascript_internal"],
)
