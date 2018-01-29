# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "sdkcompat",
    visibility = ["//visibility:public"],
    exports = select_for_plugin_api({
        "android-studio-3.0.1.0": ["//sdkcompat/v171"],
        "intellij-2017.2.6": ["//sdkcompat/v172"],
        "intellij-ue-2017.2.6": ["//sdkcompat/v172"],
        "intellij-2017.3.3": ["//sdkcompat/v173"],
        "intellij-ue-2017.3.3": ["//sdkcompat/v173"],
        "clion-2017.2.3": ["//sdkcompat/v172"],
        "clion-2017.3.2": ["//sdkcompat/v173"],
    }),
)
