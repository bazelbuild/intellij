load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2019.3": ["@intellij_ue_2019_3//:javascript"],
        "intellij-ue-2020.1": ["@intellij_ue_2020_1//:javascript"],
        "clion-2019.3": ["@clion_2019_3//:javascript"],
        "default": [],
    }),
)

java_library(
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2019.3": ["@intellij_ue_2019_3//:css"],
        "intellij-ue-2020.1": ["@intellij_ue_2020_1//:css"],
        "clion-2019.3": ["@clion_2019_3//:css"],
        "default": [],
    }),
)

java_library(
    name = "tslint_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2019.3": ["@intellij_ue_2019_3//:tslint"],
        "intellij-ue-2020.1": ["@intellij_ue_2020_1//:tslint"],
        "clion-2019.3": ["@clion_2019_3//:tslint"],
        "default": [],
    }),
)

java_library(
    name = "angular_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2019.3": ["@intellij_ue_2019_3//:angular"],
        "intellij-ue-2020.1": ["@intellij_ue_2020_1//:angular"],
        "clion-2019.3": ["@intellij_ue_2019_3//:angular"],
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
