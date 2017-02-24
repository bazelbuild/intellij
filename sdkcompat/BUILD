# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "sdkcompat",
    srcs = select_for_plugin_api({
        "android-studio-145.1617.8": ["//sdkcompat/v145"],
        "android-studio-2.3.0.3": ["//sdkcompat/v162"],
        "android-studio-2.3.0.4": ["//sdkcompat/v162"],
        "android-studio-2.3.0.6": ["//sdkcompat/v162"],
        "intellij-2016.3.1": ["//sdkcompat/v163"],
        "intellij-162.2032.8": ["//sdkcompat/v162"],
        "clion-162.1967.7": ["//sdkcompat/v162"],
        "clion-2016.3.2": ["//sdkcompat/v163"],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
        "@jsr305_annotations//jar",
    ],
)
