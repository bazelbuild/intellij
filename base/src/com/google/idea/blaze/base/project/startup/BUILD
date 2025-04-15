load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "startup",
    srcs = glob([
        "*.kt",
        "*.java",
    ]),
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
    ],
)
