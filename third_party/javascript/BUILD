load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")
load("//third_party/bazel_rules/rules_java/java:java_library.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2022.3": ["@intellij_ue_2022_3//:javascript"],
        "clion-2022.3": ["@clion_2022_3//:javascript"],
        "default": [],
    }),
)

java_library(
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2022.3": ["@intellij_ue_2022_3//:css"],
        "clion-2022.3": ["@clion_2022_3//:css"],
        "default": [],
    }),
)

java_library(
    name = "tslint_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2022.3": ["@intellij_ue_2022_3//:tslint"],
        "clion-2022.3": ["@clion_2022_3//:tslint"],
        "default": [],
    }),
)

java_library(
    name = "angular_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-ue-2022.3": ["@intellij_ue_2022_3//:angular"],
        "clion-2022.3": ["@intellij_ue_2022_3//:angular"],
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
