# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

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

java_library(
    name = "sdkcompat",
    visibility = ["//visibility:public"],
    exports = select_for_plugin_api({
        "android-studio-3.5": ["//sdkcompat/v191"],
        "android-studio-3.6": ["//sdkcompat/v192"],
        "android-studio-4.0": ["//sdkcompat/v193"],
        "intellij-2019.2": ["//sdkcompat/v192"],
        "intellij-ue-2019.2": ["//sdkcompat/v192"],
        "intellij-2019.3": ["//sdkcompat/v193"],
        "intellij-ue-2019.3": ["//sdkcompat/v193"],
        "clion-2019.2": ["//sdkcompat/v192"],
        "clion-2019.3": ["//sdkcompat/v193"],
    }),
)
