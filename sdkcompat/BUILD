# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "sdkcompat",
    visibility = ["//visibility:public"],
    exports = select_for_plugin_api({
        "android-studio-3.0": ["//sdkcompat/v171"],
        "android-studio-3.2": ["//sdkcompat/v173"],
        "intellij-2017.3": ["//sdkcompat/v173"],
        "intellij-ue-2017.3": ["//sdkcompat/v173"],
        "intellij-2018.1": ["//sdkcompat/v181"],
        "intellij-ue-2018.1": ["//sdkcompat/v181"],
        "clion-2017.3": ["//sdkcompat/v173"],
    }),
)
