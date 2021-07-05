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
        "android-studio-4.2": ["//sdkcompat/v202"],
        "android-studio-2020.3": ["//sdkcompat/v203"],
        "intellij-2020.2": ["//sdkcompat/v202"],
        "intellij-ue-2020.2": ["//sdkcompat/v202"],
        "intellij-2020.3": ["//sdkcompat/v203"],
        "intellij-ue-2020.3": ["//sdkcompat/v203"],
        "intellij-2021.1": ["//sdkcompat/v211"],
        "intellij-ue-2021.1": ["//sdkcompat/v211"],
        "intellij-2021.2": ["//sdkcompat/v212"],
        "intellij-ue-2021.2": ["//sdkcompat/v212"],
        "clion-2020.2": ["//sdkcompat/v202"],
        "clion-2020.3": ["//sdkcompat/v203"],
        "clion-2021.1": ["//sdkcompat/v211"],
    }),
)
