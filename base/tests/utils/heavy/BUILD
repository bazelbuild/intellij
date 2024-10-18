load("@rules_java//java:defs.bzl", "java_library")
load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

java_library(
    name = "lib",
    srcs = glob(["src/**/*.java"]),
    visibility = DEFAULT_TEST_VISIBILITY,
    testonly = True,
    deps = [
        "//base",
        "//shared",
        "//testing:lib",
        "//common/util:process",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//sdkcompat",
        "@junit//jar",
        "@org_opentest4j_opentest4j//jar",
    ]
)
