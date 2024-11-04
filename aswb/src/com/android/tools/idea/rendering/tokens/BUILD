load(
    "//:build-visibility.bzl",
    "ASWB_SUBPACKAGES_VISIBILITY",
    "TEST_ASWB_SUBPACKAGES_VISIBILITY",
)
load(
    "//intellij_platform_sdk:build_defs.bzl",
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
        "//java",
        "//proto:proto_deps",
        "//querysync",
        "//shared",
        "//shared:artifact",
        "//intellij_platform_sdk:plugin_api",
    ],
)
