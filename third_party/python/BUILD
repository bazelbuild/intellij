package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "python_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2017.1.5": ["@python_2017_1//:python"],
        "intellij-2017.2.3": ["@python_2017_2//:python"],
        "intellij-ue-2017.1.5": ["@python_2017_1//:python"],
        "intellij-ue-2017.2.3": ["@python_2017_2//:python"],
        "clion-2017.1.1": ["@clion_2017_1_1//:python"],
        "clion-2017.2.2": ["@clion_2017_2_2//:python"],
    }),
)

java_library(
    name = "python_for_tests",
    testonly = 1,
    exports = [":python_internal"],
)

java_library(
    name = "python",
    neverlink = 1,
    exports = [":python_internal"],
)
