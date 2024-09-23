load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "python_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2022.3": ["@python_2022_3//:python"],
        "intellij-ue-2022.3": ["@python_2022_3//:python"],
        "intellij-2023.1": ["@python_2023_1//:python"],
        "intellij-ue-2023.1": ["@python_2023_1//:python"],
        "intellij-2023.2": ["@python_2023_2//:python"],
        "intellij-ue-2023.2": ["@python_2023_2//:python"],
        "intellij-2023.3": ["@python_2023_3//:python"],
        "intellij-ue-2023.3": ["@python_2023_3//:python"],
        "intellij-2024.1": ["@python_2024_1//:python"],
        "intellij-ue-2024.1": ["@python_2024_1//:python"],
        "intellij-2024.2": ["@python_2024_2//:python"],
        "intellij-ue-2024.2": ["@python_2024_2//:python"],
        "intellij-2024.3": ["@python_2024_3//:python"],
        "intellij-ue-2024.3": ["@python_2024_3//:python"],
        "clion-2022.3": ["@clion_2022_3//:python"],
        "clion-2023.1": ["@clion_2023_1//:python"],
        "clion-2023.2": ["@clion_2023_2//:python"],
        "clion-2023.3": ["@clion_2023_3//:python"],
        "clion-2024.1": ["@clion_2024_1//:python"],
        "clion-2024.2": ["@python_2024_2//:python"],
        "clion-2024.3": ["@python_2024_3//:python"],
        "android-studio-2022.3": ["@python_2022_3//:python"],
        "android-studio-2023.1": ["@python_2023_1//:python"],
        "android-studio-2023.2": ["@python_2023_2//:python"],
    }),
)

filegroup(
    name = "python_helpers",
    srcs = select_for_plugin_api({
        "intellij-2022.3": ["@python_2022_3//:python_helpers"],
        "intellij-ue-2022.3": ["@python_2022_3//:python_helpers"],
        "intellij-2023.1": ["@python_2023_1//:python_helpers"],
        "intellij-ue-2023.1": ["@python_2023_1//:python_helpers"],
        "intellij-2023.2": ["@python_2023_2//:python_helpers"],
        "intellij-ue-2023.2": ["@python_2023_2//:python_helpers"],
        "intellij-2023.3": ["@python_2023_3//:python_helpers"],
        "intellij-ue-2023.3": ["@python_2023_3//:python_helpers"],
        "intellij-2024.1": ["@python_2024_1//:python_helpers"],
        "intellij-ue-2024.1": ["@python_2024_1//:python_helpers"],
        "intellij-2024.2": ["@python_2024_2//:python_helpers"],
        "intellij-ue-2024.2": ["@python_2024_2//:python_helpers"],
        "clion-2022.3": ["@clion_2022_3//:python_helpers"],
        "clion-2023.1": ["@clion_2023_1//:python_helpers"],
        "clion-2023.2": ["@clion_2023_2//:python_helpers"],
        "clion-2023.3": ["@clion_2023_3//:python_helpers"],
        "clion-2024.1": ["@clion_2024_1//:python_helpers"],
        "clion-2024.2": ["@python_2024_2//:python_helpers"],
        "clion-2024.3": ["@python_2024_3//:python_helpers"],
        "android-studio-2022.3": ["@python_2022_3//:python_helpers"],
        "android-studio-2023.1": ["@python_2023_1//:python_helpers"],
        "android-studio-2023.2": ["@python_2023_2//:python_helpers"],
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
