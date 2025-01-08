load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")
load("//third_party/bazel_rules/rules_java/java:java_library.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "go_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2022.3": ["@go_2022_3//:go"],
        "intellij-ue-2022.3": ["@go_2022_3//:go"],
        "default": [],
    }),
)

java_library(
    name = "go_for_tests",
    testonly = 1,
    exports = [":go_internal"],
)

java_library(
    name = "go",
    neverlink = 1,
    exports = [":go_internal"],
)
