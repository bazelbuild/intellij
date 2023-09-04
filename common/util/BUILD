load(
    "//:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
)

package(default_visibility = COMMON_PLUGINS_VISIBILITY)

licenses(["notice"])

java_library(
    name = "concurrency",
    srcs = ["src/com/google/idea/common/util/ConcurrencyUtil.java"],
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep for @Nullable
        "//intellij_platform_sdk:plugin_api",
    ],
)

java_library(
    name = "platform",
    srcs = ["src/com/google/idea/common/util/MorePlatformUtils.java"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
    ],
)

java_library(
    name = "transactions",
    srcs = ["src/com/google/idea/common/util/Transactions.java"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
    ],
)

java_library(
    name = "process",
    srcs = glob(["src/com/google/idea/async/process/*.java"]),
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep for @Nullable
        "//intellij_platform_sdk:plugin_api",
    ],
)

java_test(
    name = "CommandLineTaskTest",
    size = "medium",
    srcs = ["tests/unittests/com/google/idea/common/async/process/CommandLineTaskTest.java"],
    test_class = "com.google.idea.common.async.process.CommandLineTaskTest",
    deps = [
        ":process",
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep for @Nullable
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//intellij_platform_sdk:truth",
        "@junit//jar",
    ],
)
