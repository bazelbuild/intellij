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
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep for @Nullable
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//shared:artifact",
        "//shared:exception",
        "@com_google_guava_guava//jar",
    ],
)
