load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load(
    "//:build-visibility.bzl",
    "G3PLUGINS_VISIBILITY",
)

kt_jvm_library(
    name = "bepparser",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//common/experiments",
        "//proto:proto_deps",
        "//shared:artifact",
        "//shared:exception",
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep for @Nullable
        "@com_google_guava_guava//jar",
    ],
)
