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
        "intellij-2024.2": ["//sdkcompat/v242"],
        "intellij-2024.3": ["//sdkcompat/v243"],
        "intellij-ue-2024.2": ["//sdkcompat/v242"],
        "intellij-ue-2024.3": ["//sdkcompat/v243"],
        "clion-2024.2": ["//sdkcompat/v242"],
        "clion-2024.3": ["//sdkcompat/v243"],
    }),
)
