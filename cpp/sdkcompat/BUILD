load(
    "//intellij_platform_sdk:build_defs.bzl",
    "select_for_plugin_api",
)

java_library(
    name = "sdkcompat",
    srcs = select_for_plugin_api({
        "clion-2024.2": ["//cpp/sdkcompat/v242"],
        "clion-2024.3": ["//cpp/sdkcompat/v243"],
    }),
    visibility = ["//cpp:__pkg__"],
    deps = ["//intellij_platform_sdk:plugin_api"],
)
