load("@rules_java//java:java_library.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)

java_library(
    name = "vcs",
    srcs = glob(["src/com/google/idea/bazel/vcs/*.java"]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
    ],
)
