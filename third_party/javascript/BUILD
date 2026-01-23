load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_version")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "javascript_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_version({
        "2025.2": ["@clion_2025_2//:javascript"],
        "2025.3": ["@clion_2025_3//:javascript"],
    }),
)

java_library(
    name = "css_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_version({
        "2025.2": ["@clion_2025_2//:css"],
        "2025.3": ["@clion_2025_3//:css"],
    }),
)

java_library(
    name = "tslint_internal",
    tags = ["no-ide"],
    visibility = ["//visibility:private"],
    exports = select_for_version({
        "2025.2": ["@clion_2025_2//:tslint"],
        "2025.3": ["@clion_2025_3//:tslint"],
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
