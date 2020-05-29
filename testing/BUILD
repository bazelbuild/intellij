# Description:
#
# Common test utilities for IntelliJ plugins.

load(
    "//intellij_platform_sdk:build_defs.bzl",
    "select_for_ide",
    "select_for_plugin_api",
)

package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache 2.0

java_library(
    name = "lib",
    testonly = 1,
    srcs = glob(["src/**/*.java"]) +
           select_for_ide(
               android_studio = glob(["studio/**/*.java"]),
               default = [],
           ) + select_for_plugin_api({
        "android-studio-4.0": glob(["cidr/**/*.java"]),
        "android-studio-4.1": glob(["cidr/**/*.java"]),
        "default": [],
    }) + select_for_plugin_api({
        "clion-2019.2": glob(
            ["testcompat/v192/**/*.java"],
            exclude = ["testcompat/v192/com/google/idea/sdkcompat/testframework/JavaResolveTestCase.java"],
        ),
        "clion-2019.3": glob(
            ["testcompat/v193/**/*.java"],
            exclude = ["testcompat/v193/com/google/idea/sdkcompat/testframework/JavaResolveTestCase.java"],
        ),
        "intellij-2019.2": glob(["testcompat/v192/**/*.java"]),
        "intellij-ue-2019.2": glob(["testcompat/v192/**/*.java"]),
        "default": glob(["testcompat/v193/**/*.java"]),
    }),
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//sdkcompat",
        "@junit//jar",
    ],
)
