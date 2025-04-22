load("@rules_java//java:java_library.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "ASWB_SUBPACKAGES_VISIBILITY",
    "TEST_ASWB_SUBPACKAGES_VISIBILITY",
)
load(
    "//build_defs:build_defs.bzl",
    "combine_visibilities",
)

java_library(
    name = "tokens",
    srcs = glob(["*.java"]),
    visibility = combine_visibilities(
        ASWB_SUBPACKAGES_VISIBILITY,
        TEST_ASWB_SUBPACKAGES_VISIBILITY,
    ),
    deps = [
        "//aswb:aswb_lib",
        "//base",
        "//common/experiments",
        "//cpp",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "//querysync",
        "//shared",
        "//shared:artifact",
    ],
)
