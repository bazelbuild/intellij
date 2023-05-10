load(
    "//:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "settings",
    srcs = glob(["src/**/*.java"]),
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/auto_value",
    ],
)
