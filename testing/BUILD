# Description:
#
# Common test utilities for IntelliJ plugins.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
)
load(
    "//intellij_platform_sdk:build_defs.bzl",
    "select_for_ide",
    "select_for_plugin_api",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

java_library(
    name = "lib",
    testonly = 1,
    srcs = glob(["src/**/*.java"]) + select_for_plugin_api({
        "clion-2024.2": glob([
            "testcompat/v242/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v242/com/google/idea/testing/*.java",
        ]),
        "clion-2024.3": glob([
            "testcompat/v243/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v243/com/google/idea/testing/*.java",
        ]),
        "clion-2025.1": glob([
            "testcompat/v251/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v251/com/google/idea/testing/*.java",
        ]),
        "intellij-2024.2": glob([
            "testcompat/v242/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v242/com/google/idea/testing/**/*.java",
            "testcompat/v242/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2024.2": glob([
            "testcompat/v242/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v242/com/google/idea/testing/**/*.java",
            "testcompat/v242/com/google/idea/java/**/*.java",
        ]),
        "intellij-2024.3": glob([
            "testcompat/v243/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v243/com/google/idea/testing/**/*.java",
            "testcompat/v243/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2024.3": glob([
            "testcompat/v243/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v243/com/google/idea/testing/**/*.java",
            "testcompat/v243/com/google/idea/java/**/*.java",
        ]),
        "intellij-2025.1": glob([
            "testcompat/v251/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v251/com/google/idea/testing/**/*.java",
            "testcompat/v251/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2025.1": glob([
            "testcompat/v251/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v251/com/google/idea/testing/**/*.java",
            "testcompat/v251/com/google/idea/java/**/*.java",
        ]),
        "default": [],
    }),
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
