# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "sdkcompat",
    visibility = ["//visibility:public"],
    exports = select_for_plugin_api({
        "android-studio-2.3.0.8": ["//sdkcompat/v162"],
        "android-studio-2.3.1.0": ["//sdkcompat/v162"],
        "intellij-2017.1.1": ["//sdkcompat/v171"],
        "intellij-2016.3.1": ["//sdkcompat/v163"],
        "clion-162.1967.7": ["//sdkcompat/v162"],
        "clion-2016.3.2": ["//sdkcompat/v163"],
        "clion-2017.1.1": ["//sdkcompat/v171"],
    }),
)
