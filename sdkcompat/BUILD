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
        "android-studio-3.0": ["//sdkcompat/v171"],
        "android-studio-3.2": ["//sdkcompat/v181"],
        "android-studio-3.3": ["//sdkcompat/v181"],
        "intellij-2018.1": ["//sdkcompat/v181"],
        "intellij-ue-2018.1": ["//sdkcompat/v181"],
        "intellij-2018.2": ["//sdkcompat/v182"],
        "intellij-ue-2018.2": ["//sdkcompat/v182"],
        "clion-2018.1": ["//sdkcompat/v181"],
        "clion-2018.2": ["//sdkcompat/v182"],
    }),
)

filegroup(
    name = "sdkcompat_plugin_xml",
    srcs = select_for_plugin_api({
        "android-studio-3.0": ["//sdkcompat/v171:plugin_xml"],
        "default": [],
    }),
)
