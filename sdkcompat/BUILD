# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

package_group(
    name = "visibility",
    includes = [
        "//common:common_plugins",
    ],
    packages = [
        "//common/...",
        "//testing",
    ],
)

package(default_visibility = [":visibility"])

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "sdkcompat",
    visibility = ["//visibility:public"],
    exports = select_for_plugin_api({
        "android-studio-3.3": ["//sdkcompat/v182"],
        "android-studio-3.4": ["//sdkcompat/v183"],
        "android-studio-3.5": ["//sdkcompat/v183"],
        "intellij-2018.3": ["//sdkcompat/v183"],
        "intellij-ue-2018.3": ["//sdkcompat/v183"],
        "clion-2018.3": ["//sdkcompat/v183"],
    }),
)
