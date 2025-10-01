load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    exports = select_for_plugin_api({
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:javascript"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:javascript"],
        "intellij-ue-2025.3": ["@intellij_ue_2025_3//:javascript"],
        "default": [],
    }),
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
)

java_library(
    exports = select_for_plugin_api({
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:css"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:css"],
        "intellij-ue-2025.3": ["@intellij_ue_2025_3//:css"],
        "default": [],
    }),
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
)

java_library(
    exports = select_for_plugin_api({
        "intellij-ue-2025.1": ["@intellij_ue_2025_1//:tslint"],
        "intellij-ue-2025.2": ["@intellij_ue_2025_2//:tslint"],
        "intellij-ue-2025.3": ["@intellij_ue_2025_3//:tslint"],
        "default": [],
    }),
    name = "tslint_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
)

java_library(
    exports = [
        ":css_internal",
        ":javascript_internal",
    ],
    name = "javascript_for_tests",
    testonly = 1,
)

java_library(
    exports = [
        ":javascript_internal",
    ],
    name = "javascript",
    neverlink = 1,
)
