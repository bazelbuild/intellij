# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")
load(
    "//:build-visibility.bzl",
    "SDK_COMPAT_VISIBILITY",
    "create_sdkcompat_visibility_package",
)

licenses(["notice"])  # Apache 2.0

create_sdkcompat_visibility_package()

java_library(
    name = "sdkcompat",
    visibility = SDK_COMPAT_VISIBILITY,
    exports = select_for_plugin_api({
        "android-studio-2020.3": ["//sdkcompat/v203"],
        "android-studio-2021.1": ["//sdkcompat/v211"],
        "android-studio-2021.2": ["//sdkcompat/v212"],
        "intellij-2020.3": ["//sdkcompat/v203"],
        "intellij-ue-2020.3": ["//sdkcompat/v203"],
        "intellij-2021.1": ["//sdkcompat/v211"],
        "intellij-ue-2021.1": ["//sdkcompat/v211"],
        "intellij-2021.2": ["//sdkcompat/v212"],
        "intellij-ue-2021.2": ["//sdkcompat/v212"],
        "intellij-2021.3": ["//sdkcompat/v213"],
        "intellij-ue-2021.3": ["//sdkcompat/v213"],
        "clion-2020.3": ["//sdkcompat/v203"],
        "clion-2021.1": ["//sdkcompat/v211"],
        "clion-2021.2": ["//sdkcompat/v212"],
        "clion-2021.3": ["//sdkcompat/v213"],
    }),
)
