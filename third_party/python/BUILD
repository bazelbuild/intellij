load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "python_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2021.2": ["@python_2021_2//:python"],
        "intellij-ue-2021.2": ["@python_2021_2//:python"],
        "intellij-2021.3": ["@python_2021_3//:python"],
        "intellij-ue-2021.3": ["@python_2021_3//:python"],
        "intellij-2022.1": ["@python_2022_1//:python"],
        "intellij-ue-2022.1": ["@python_2022_1//:python"],
        "clion-2021.2": ["@clion_2021_2//:python"],
        "clion-2021.3": ["@clion_2021_3//:python"],
        "clion-2022.1": ["@clion_2022_1//:python"],
        "android-studio-2020.3": ["@python_2020_3//:python"],
        "android-studio-2021.2": ["@python_2021_2//:python"],
    }),
)

java_library(
    name = "python_for_tests",
    testonly = 1,
    data = glob(["helpers/**"]),
    exports = [":python_internal"],
)

java_library(
    name = "python",
    neverlink = 1,
    exports = [":python_internal"],
)
