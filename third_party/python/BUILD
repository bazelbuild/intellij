load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "python_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2019.3": ["@python_2019_3//:python"],
        "intellij-ue-2019.3": ["@python_2019_3//:python"],
        "intellij-2020.1": ["@python_2020_1//:python"],
        "intellij-ue-2020.1": ["@python_2020_1//:python"],
        "clion-2019.3": ["@clion_2019_3//:python"],
        "clion-2020.1": ["@clion_2020_1//:python"],
        "android-studio-4.0": ["@python_2019_3//:python"],
        "android-studio-4.1": ["@python_2019_3//:python"],
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
