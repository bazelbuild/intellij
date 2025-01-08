load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")
load("//third_party/bazel_rules/rules_java/java:java_library.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "python_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2022.3": ["@python_2022_3//:python"],
        "intellij-ue-2022.3": ["@python_2022_3//:python"],
        "clion-2022.3": ["@clion_2022_3//:python"],
        "android-studio-2023.3": ["@python_2023_3//:python"],
    }),
)

filegroup(
    name = "python_helpers",
    srcs = select_for_plugin_api({
        "intellij-2022.3": ["@python_2022_3//:python_helpers"],
        "intellij-ue-2022.3": ["@python_2022_3//:python_helpers"],
        "clion-2022.3": ["@clion_2022_3//:python_helpers"],
        "android-studio-2023.3": ["@python_2023_3//:python_helpers"],
    }),
    visibility = ["//visibility:private"],
)

java_library(
    name = "python_for_tests",
    testonly = 1,
    data = [":python_helpers"],
    exports = [":python_internal"],
)

java_library(
    name = "python",
    neverlink = 1,
    exports = [":python_internal"],
)
