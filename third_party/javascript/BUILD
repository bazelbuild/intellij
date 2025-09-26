load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_version")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    exports = select_for_version({
        "2025.1": ["@clion_2025_1//:javascript"],
        "2025.2": ["@clion_2025_2//:javascript"],
        "2025.3": ["@clion_2025_3//:javascript"],
    }),
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
)

java_library(
    exports = select_for_version({
        "2025.1": ["@clion_2025_1//:css"],
        "2025.2": ["@clion_2025_2//:css"],
        "2025.3": ["@clion_2025_3//:css"],
    }),
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
)

java_library(
    exports = select_for_version({
        "2025.1": ["@clion_2025_1//:tslint"],
        "2025.2": ["@clion_2025_2//:tslint"],
        "2025.3": ["@clion_2025_3//:tslint"],
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
