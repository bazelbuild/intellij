# Description: Indirections for SDK changes to the underlying platform library.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "SDK_COMPAT_VISIBILITY",
    "create_sdkcompat_visibility_package",
)
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

licenses(["notice"])

create_sdkcompat_visibility_package()

java_library(
    name = "sdkcompat",
    visibility = SDK_COMPAT_VISIBILITY,
    exports = select_for_plugin_api({
        "android-studio-2022.2": ["//sdkcompat/v222"],
        "android-studio-2022.3": ["//sdkcompat/v223"],
        "android-studio-2023.1": ["//sdkcompat/v231"],
        "android-studio-2023.2": ["//sdkcompat/v232"],
        "android-studio-2023.3": ["//sdkcompat/v233"],
        "intellij-2022.1": ["//sdkcompat/v221"],
        "intellij-ue-2022.1": ["//sdkcompat/v221"],
        "intellij-2022.2": ["//sdkcompat/v222"],
        "intellij-2022.3": ["//sdkcompat/v223"],
        "intellij-ue-2022.2": ["//sdkcompat/v222"],
        "intellij-ue-2022.3": ["//sdkcompat/v223"],
        "clion-2022.1": ["//sdkcompat/v221"],
        "clion-2022.2": ["//sdkcompat/v222"],
        "clion-2022.3": ["//sdkcompat/v223"],
    }),
)
