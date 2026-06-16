load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_version")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_version({
        "2025.3": ["@clion_2025_3//:javascript"],
        "2026.1": ["@clion_2026_1//:javascript"],
        "2026.2": ["@clion_2026_2//:javascript"],
    }),
)

java_library(
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_version({
        "2025.3": ["@clion_2025_3//:css"],
        "2026.1": ["@clion_2026_1//:css"],
        "2026.2": ["@clion_2026_2//:css"],
    }),
)

java_library(
    name = "tslint_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_version({
        "2025.3": ["@clion_2025_3//:tslint"],
        "2026.1": ["@clion_2026_1//:tslint"],
        "2026.2": ["@clion_2026_2//:tslint"],
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
