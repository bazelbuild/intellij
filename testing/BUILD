# Description:
#
# Common test utilities for IntelliJ plugins.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

java_library(
    name = "lib",
    testonly = 1,
    srcs = glob(["src/**/*.java"]),
    exports = ["//testing/src/com/google/idea/testing/runfiles"],
    runtime_deps = [
        "@org_opentest4j_opentest4j//jar",  # Needed by the IntelliJ test framework.
    ],
    deps = [
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//third_party/java/junit",
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "runfiles",
    testonly = 1,
    exports = ["//testing/src/com/google/idea/testing/runfiles"],
)
