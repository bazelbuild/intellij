load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2024.3": ["@intellij_ue_2024_3//:javascript"],
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:javascript"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:javascript"],
        "clion-2024.3": ["@clion_2024_3//:javascript"],
        "clion-2025.1": ["@clion_2025_1//:javascript"],
        "clion-2025.2": ["@clion_2025_2//:javascript"],
        "default": [],
    }),
)

java_library(
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2024.3": ["@intellij_ue_2024_3//:css"],
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:css"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:css"],
        "clion-2024.3": ["@clion_2024_3//:css"],
        "clion-2025.1": ["@clion_2025_1//:css"],
        "clion-2025.2": ["@clion_2025_2//:css"],
        "default": [],
    }),
)

java_library(
    name = "tslint_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2024.3": ["@intellij_ue_2024_3//:tslint"],
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:tslint"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:tslint"],
        "clion-2024.3": ["@clion_2024_3//:tslint"],
        "clion-2025.1": ["@clion_2025_1//:tslint"],
        "clion-2025.2": ["@clion_2025_2//:tslint"],
        "default": [],
    }),
)

java_library(
    name = "angular_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2024.3": ["@intellij_ue_2024_3//:angular"],
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:angular"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:angular"],
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
