package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "kotlin_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2017.2.6": ["@kotlin_2017_2//:kotlin"],
        "intellij-2017.3.3": ["@kotlin_2017_3//:kotlin"],
    }),
)

java_library(
    name = "kotlin_for_tests",
    testonly = 1,
    exports = [":kotlin_internal"],
    runtime_deps =  select_for_plugin_api({
        "intellij-2017.2.6": ["@kotlin_2017_2//:integration-test-deps"],
        "intellij-2017.3.3": ["@kotlin_2017_3//:integration-test-deps"],
    }),
)

java_library(
    name = "kotlin",
    neverlink = 1,
    exports = [":kotlin_internal"]
)

