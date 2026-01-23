load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "python_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2025.1": ["@python_2025_1//:python"],
        "intellij-2025.2": ["@python_2025_2//:python"],
        "intellij-2025.3": ["@python_2025_3//:python"],
        "intellij-2026.1": ["@python_2026_1//:python"],
        "intellij-ue-2025.1": ["@python_2025_1//:python"],
        "intellij-ue-2025.2": ["@python_2025_2//:python"],
        "intellij-ue-2025.3": ["@python_2025_3//:python"],
        "intellij-ue-2026.1": ["@python_2026_1//:python"],
    }),
)

filegroup(
    name = "python_helpers",
    srcs = select_for_plugin_api({
        "intellij-2025.1": ["@python_2025_1//:python_helpers"],
        "intellij-2025.2": ["@python_2025_2//:python_helpers"],
        "intellij-2025.3": ["@python_2025_3//:python_helpers"],
        "intellij-2026.1": ["@python_2026_1//:python_helpers"],
        "intellij-ue-2025.1": ["@python_2025_1//:python_helpers"],
        "intellij-ue-2025.2": ["@python_2025_2//:python_helpers"],
        "intellij-ue-2025.3": ["@python_2025_3//:python_helpers"],
        "intellij-ue-2026.1": ["@python_2026_1//:python_helpers"],
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
