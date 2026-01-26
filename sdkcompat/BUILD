# Description: Indirections for SDK changes to the underlying platform library.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "SDK_COMPAT_VISIBILITY",
    "create_sdkcompat_visibility_package",
)
load("//intellij_platform_sdk:build_defs.bzl", "select_for_version")

licenses(["notice"])

create_sdkcompat_visibility_package()

java_library(
    name = "sdkcompat",
    visibility = SDK_COMPAT_VISIBILITY,
    exports = select_for_version({
        "2025.2": ["//sdkcompat/v252"],
        "2025.3": ["//sdkcompat/v253"],
        "2026.1": ["//sdkcompat/v261"],
    }),
)

#api252 needs to be a separate target since radler sources are not always available
java_library(
    name = "sdkcompat_radler",
    visibility = SDK_COMPAT_VISIBILITY,
    exports = select_for_version({
        "2025.2": ["//sdkcompat/v252:v252_radler"],
        "2025.3": ["//sdkcompat/v253:v253_radler"],
        "2026.1": ["//sdkcompat/v261:v261_radler"],
    }),
)
