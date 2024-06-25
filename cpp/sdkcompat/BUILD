load(
    "//intellij_platform_sdk:build_defs.bzl",
    "select_for_plugin_api",
)

java_library(
    name = "sdkcompat",
    srcs = select_for_plugin_api({
        "android-studio-2022.3": ["//cpp/sdkcompat/v223"],
        "android-studio-2023.1": ["//cpp/sdkcompat/v231"],
        "android-studio-2023.2": ["//cpp/sdkcompat/v232"],
        "clion-2022.3": ["//cpp/sdkcompat/v223"],
        "clion-2023.1": ["//cpp/sdkcompat/v231"],
        "clion-2023.2": ["//cpp/sdkcompat/v232"],
        "clion-2023.3": ["//cpp/sdkcompat/v233"],
        "clion-2024.1": ["//cpp/sdkcompat/v241"],
        "clion-2024.2": ["//cpp/sdkcompat/v242"],
    }),
    visibility = ["//cpp:__pkg__"],
    deps = ["//intellij_platform_sdk:plugin_api"],
)
