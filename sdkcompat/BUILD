# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "sdkcompat",
    visibility = ["//visibility:public"],
    exports = select_for_plugin_api({
        "android-studio-3.0.0.9": ["//sdkcompat/v171"],
        "android-studio-2.3.1.0": ["//sdkcompat/v162"],
        "intellij-2017.2.2": ["//sdkcompat/v172"],
        "intellij-2017.1.5": ["//sdkcompat/v171"],
        "intellij-ue-2017.2.2": ["//sdkcompat/v172"],
        "intellij-ue-2017.1.5": ["//sdkcompat/v171"],
        "clion-2017.2.1": ["//sdkcompat/v172"],
        "clion-2017.1.1": ["//sdkcompat/v171"],
    }),
)
