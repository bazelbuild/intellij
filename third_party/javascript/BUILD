package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "javascript_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2018.2": ["@intellij_ue_2018_2//:javascript"],
        "intellij-ue-2018.2": ["@intellij_ue_2018_2//:javascript"],
        "intellij-2018.3": ["@intellij_ue_2018_3//:javascript"],
        "intellij-ue-2018.3": ["@intellij_ue_2018_3//:javascript"],
        "clion-2018.2": ["@clion_2018_2//:javascript"],
        "clion-2018.3": ["@clion_2018_3//:javascript"],
        "default": [],
    }),
)

java_library(
    name = "css_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2018.2": ["@intellij_ue_2018_2//:css"],
        "intellij-ue-2018.2": ["@intellij_ue_2018_2//:css"],
        "intellij-2018.3": ["@intellij_ue_2018_3//:css"],
        "intellij-ue-2018.3": ["@intellij_ue_2018_3//:css"],
        "clion-2018.2": ["@clion_2018_2//:css"],
        "clion-2018.3": ["@clion_2018_3//:css"],
        "default": [],
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
