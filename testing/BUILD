# Description:
#
# Common test utilities for IntelliJ plugins.

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

licenses(["notice"])  # Apache 2.0

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
        "android-studio-2020.3": glob(["testcompat/v203/com/google/idea/sdkcompat/**/*.java"]) + glob([
            "testcompat/v203/com/google/idea/testing/cidr/*.java",
        ]),
        "android-studio-2021.1": glob(["testcompat/v211/com/google/idea/sdkcompat/**/*.java"]) + glob([
            "testcompat/v211/com/google/idea/testing/cidr/*.java",
        ]),
        "android-studio-2021.2": glob(["testcompat/v212/com/google/idea/sdkcompat/**/*.java"]) + glob([
            "testcompat/v212/com/google/idea/testing/cidr/*.java",
        ]),
        "clion-2020.3": glob(["testcompat/v203/com/google/idea/sdkcompat/**/*.java"]),
        "clion-2021.1": glob(["testcompat/v211/com/google/idea/sdkcompat/**/*.java"]),
        "clion-2021.2": glob(["testcompat/v212/com/google/idea/sdkcompat/**/*.java"]),
        "clion-2021.3": glob(["testcompat/v213/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-2020.3": glob(["testcompat/v203/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-ue-2020.3": glob(["testcompat/v203/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-2021.1": glob(["testcompat/v211/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-ue-2021.1": glob(["testcompat/v211/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-2021.2": glob(["testcompat/v212/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-ue-2021.2": glob(["testcompat/v212/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-2021.3": glob(["testcompat/v213/com/google/idea/sdkcompat/**/*.java"]),
        "intellij-ue-2021.3": glob(["testcompat/v213/com/google/idea/sdkcompat/**/*.java"]),
        "default": glob(["testcompat/v203/com/google/idea/sdkcompat/**/*.java"]),
    }),
    deps = [
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "@junit//jar",
    ],
)
