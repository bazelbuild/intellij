# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")
load(
    "//:build-visibility.bzl",
    "SDK_COMPAT_VISIBILITY",
    "create_sdkcompat_visibility_package",
)

licenses(["notice"])

create_sdkcompat_visibility_package()

java_library(
    name = "sdkcompat",
    visibility = SDK_COMPAT_VISIBILITY,
    exports = select_for_plugin_api({
        "clion-2024.1": ["//sdkcompat/v241"],
        "intellij-ue-2024.1": ["//sdkcompat/v241"],
        "intellij-2024.1": ["//sdkcompat/v241"],
        "android-studio-2022.2": ["//sdkcompat/v222"],
        "android-studio-2022.3": ["//sdkcompat/v223"],
        "android-studio-2023.1": ["//sdkcompat/v231"],
        "android-studio-2023.2": ["//sdkcompat/v232"],
        "intellij-2021.3": ["//sdkcompat/v213"],
        "intellij-ue-2021.3": ["//sdkcompat/v213"],
        "intellij-2022.1": ["//sdkcompat/v221"],
        "intellij-ue-2022.1": ["//sdkcompat/v221"],
        "intellij-2022.2": ["//sdkcompat/v222"],
        "intellij-ue-2022.2": ["//sdkcompat/v222"],
        "intellij-2022.3": ["//sdkcompat/v223"],
        "intellij-ue-2022.3": ["//sdkcompat/v223"],
        "intellij-2023.1": ["//sdkcompat/v231"],
        "intellij-ue-2023.1": ["//sdkcompat/v231"],
        "intellij-2023.2": ["//sdkcompat/v232"],
        "intellij-ue-2023.2": ["//sdkcompat/v232"],
        "intellij-2023.3": ["//sdkcompat/v233"],
        "intellij-ue-2023.3": ["//sdkcompat/v233"],
        "clion-2021.3": ["//sdkcompat/v213"],
        "clion-2022.1": ["//sdkcompat/v221"],
        "clion-2022.2": ["//sdkcompat/v222"],
        "clion-2022.3": ["//sdkcompat/v223"],
        "clion-2023.1": ["//sdkcompat/v231"],
        "clion-2023.2": ["//sdkcompat/v232"],
        "clion-2023.3": ["//sdkcompat/v233"],
        "clion-2024.1": ["//sdkcompat/v241"],
    }),
)
