package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2019.2": ["@intellij_ue_2019_2//:javascript"],
        "intellij-ue-2019.2": ["@intellij_ue_2019_2//:javascript"],
        "intellij-2019.3": ["@intellij_ue_2019_3//:javascript"],
        "intellij-ue-2019.3": ["@intellij_ue_2019_3//:javascript"],
        "clion-2019.2": ["@clion_2019_2//:javascript"],
        "clion-2019.3": ["@clion_2019_3//:javascript"],
        "default": [],
    }),
)

java_library(
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2019.2": ["@intellij_ue_2019_2//:css"],
        "intellij-ue-2019.2": ["@intellij_ue_2019_2//:css"],
        "intellij-2019.3": ["@intellij_ue_2019_3//:css"],
        "intellij-ue-2019.3": ["@intellij_ue_2019_3//:css"],
        "clion-2019.2": ["@clion_2019_2//:css"],
        "default": [],
    }),
)

java_library(
    name = "angular_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2019.2": ["@intellij_ue_2019_2//:angular"],
        "intellij-ue-2019.2": ["@intellij_ue_2019_2//:angular"],
        "intellij-2019.3": ["@intellij_ue_2019_3//:angular"],
        "intellij-ue-2019.3": ["@intellij_ue_2019_3//:angular"],
        "clion-2019.2": ["@intellij_ue_2019_2//:angular"],
        "default": [],
    }),
)

java_library(
    name = "javascript_for_tests",
    testonly = 1,
    exports = [
        ":angular_internal",
        ":css_internal",
        ":javascript_internal",
    ],
)

java_library(
    name = "javascript",
    neverlink = 1,
    exports = [
        ":angular_internal",
        ":javascript_internal",
    ],
)
