load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_version")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "python_internal",
    visibility = ["//visibility:private"],
    exports = select_for_version({
        "2025.2": ["@python_2025_2//:python"],
        "2025.3": ["@python_2025_3//:python"],
    }),
)

filegroup(
    name = "python_helpers",
    srcs = select_for_version({
        "2025.2": ["@python_2025_2//:python_helpers"],
        "2025.3": ["@python_2025_3//:python_helpers"],
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
