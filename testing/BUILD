# Description:
#
# Common test utilities for IntelliJ plugins.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//intellij_platform_sdk:build_defs.bzl",
    "select_for_ide",
    "select_for_plugin_api",
)
load(
    "//:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

java_library(
    name = "lib",
    testonly = 1,
    srcs = glob(["src/**/*.java"]) +
           select_for_ide(
               android_studio = glob([
                   "studio/**/*.java",
                   "cidr/**/*.java",
               ]),
               default = [],
           ) + select_for_plugin_api({
        "android-studio-2022.3": glob([
            "testcompat/v223/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v223/com/google/idea/testing/**/*.java",
            "testcompat/v223/com/google/idea/java/**/*.java",
        ]),
        "android-studio-2023.1": glob([
            "testcompat/v231/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v231/com/google/idea/testing/**/*.java",
            "testcompat/v231/com/google/idea/java/**/*.java",
        ]),
        "android-studio-2023.2": glob([
            "testcompat/v232/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v232/com/google/idea/testing/**/*.java",
            "testcompat/v232/com/google/idea/java/**/*.java",
        ]),
        "clion-2022.3": glob([
            "testcompat/v223/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v223/com/google/idea/testing/*.java",
        ]),
        "clion-2023.1": glob([
            "testcompat/v231/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v231/com/google/idea/testing/*.java",
        ]),
        "clion-2023.2": glob([
            "testcompat/v232/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v232/com/google/idea/testing/*.java",
        ]),
        "clion-2023.3": glob([
            "testcompat/v233/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v233/com/google/idea/testing/*.java",
        ]),
        "clion-2024.1": glob([
            "testcompat/v241/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v241/com/google/idea/testing/*.java",
        ]),
        "clion-2024.2": glob([
            "testcompat/v242/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v242/com/google/idea/testing/*.java",
        ]),
        "clion-2024.3": glob([
            "testcompat/v243/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v243/com/google/idea/testing/*.java",
        ]),
        "intellij-2022.3": glob([
            "testcompat/v223/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v223/com/google/idea/testing/**/*.java",
            "testcompat/v223/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2022.3": glob([
            "testcompat/v223/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v223/com/google/idea/testing/**/*.java",
            "testcompat/v223/com/google/idea/java/**/*.java",
        ]),
        "intellij-2023.1": glob([
            "testcompat/v231/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v231/com/google/idea/testing/**/*.java",
            "testcompat/v231/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2023.1": glob([
            "testcompat/v231/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v231/com/google/idea/testing/**/*.java",
            "testcompat/v231/com/google/idea/java/**/*.java",
        ]),
        "intellij-2023.2": glob([
            "testcompat/v232/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v232/com/google/idea/testing/**/*.java",
            "testcompat/v232/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2023.2": glob([
            "testcompat/v232/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v232/com/google/idea/testing/**/*.java",
            "testcompat/v232/com/google/idea/java/**/*.java",
        ]),
        "intellij-2023.3": glob([
            "testcompat/v233/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v233/com/google/idea/testing/**/*.java",
            "testcompat/v233/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2023.3": glob([
            "testcompat/v233/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v233/com/google/idea/testing/**/*.java",
            "testcompat/v233/com/google/idea/java/**/*.java",
        ]),
        "intellij-2024.1": glob([
            "testcompat/v241/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v241/com/google/idea/testing/**/*.java",
            "testcompat/v241/com/google/idea/java/**/*.java",
        ]),
        "intellij-ue-2024.1": glob([
            "testcompat/v241/com/google/idea/sdkcompat/**/*.java",
            "testcompat/v241/com/google/idea/testing/**/*.java",
            "testcompat/v241/com/google/idea/java/**/*.java",
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
        "default": [],
    }),
    exports = ["//testing/src/com/google/idea/testing/runfiles"],
    deps = [
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@junit//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "runfiles",
    testonly = 1,
    exports = ["//testing/src/com/google/idea/testing/runfiles"],
)
