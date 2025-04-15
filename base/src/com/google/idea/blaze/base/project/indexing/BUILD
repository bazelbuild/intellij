load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "indexing",
    srcs = glob([
        "*.kt",
        "*.java",
    ]),
    visibility = [
        "//base:__subpackages__",
    ],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
        "//querysync",
    ],
)
