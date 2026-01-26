load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:javascript"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:javascript"],
        "intellij-ue-2025.3": ["@intellij_ue_2025_3//:javascript"],
        "intellij-ue-2026.1": ["@intellij_ue_2026_1//:javascript"],
        "default": [],
    }),
)

java_library(
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:css"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:css"],
        "intellij-ue-2025.3": ["@intellij_ue_2025_3//:css"],
        "intellij-ue-2026.1": ["@intellij_ue_2026_1//:css"],
        "default": [],
    }),
)

java_library(
    name = "tslint_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:tslint"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:tslint"],
        "intellij-ue-2025.3": ["@intellij_ue_2025_3//:tslint"],
        "intellij-ue-2026.1": ["@intellij_ue_2026_1//:tslint"],
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
    exports = [
        ":javascript_internal",
    ],
)
