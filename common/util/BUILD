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
    srcs = [
        "src/com/google/idea/common/util/MorePlatformUtils.java",
        ":channel_java",
    ],
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

genrule(
    name = "stable",
    outs = ["stable.txt"],
    cmd = "echo stable > $@",
)

genrule(
    name = "beta",
    outs = ["beta.txt"],
    cmd = "echo beta > $@",
)

genrule(
    name = "canary",
    outs = ["canary.txt"],
    cmd = "echo canary > $@",
)

genrule(
    name = "channel_java",
    srcs = select(
        {
            "//intellij_platform_sdk:intellij-latest": [":stable"],
            "//intellij_platform_sdk:intellij-latest-mac": [":stable"],
            "//intellij_platform_sdk:intellij-beta": [":beta"],
            "//intellij_platform_sdk:intellij-under-dev": [":canary"],
            "//intellij_platform_sdk:intellij-oss-latest-stable": [":stable"],
            "//intellij_platform_sdk:intellij-oss-oldest-stable": [":stable"],
            "//intellij_platform_sdk:intellij-oss-under-dev": [":canary"],
            "//intellij_platform_sdk:intellij-ue-latest": [":stable"],
            "//intellij_platform_sdk:intellij-ue-latest-mac": [":stable"],
            "//intellij_platform_sdk:intellij-ue-beta": [":beta"],
            "//intellij_platform_sdk:intellij-ue-oss-latest-stable": [":stable"],
            "//intellij_platform_sdk:intellij-ue-oss-oldest-stable": [":stable"],
            "//intellij_platform_sdk:intellij-ue-oss-under-dev": [":canary"],
            "//intellij_platform_sdk:intellij-ue-cc-latest-stable": [":stable"],
            "//intellij_platform_sdk:intellij-ue-cc-oldest-stable": [":stable"],
            "//intellij_platform_sdk:intellij-ue-cc-under-dev": [":canary"],
            "//intellij_platform_sdk:clion-latest": [":stable"],
            "//intellij_platform_sdk:clion-latest-mac": [":stable"],
            "//intellij_platform_sdk:clion-beta": [":beta"],
            "//intellij_platform_sdk:clion-under-dev": [":canary"],
            "//intellij_platform_sdk:clion-oss-latest-stable": [":stable"],
            "//intellij_platform_sdk:clion-oss-oldest-stable": [":stable"],
            "//intellij_platform_sdk:clion-oss-under-dev": [":canary"],
            "//intellij_platform_sdk:android-studio-latest": [":stable"],
            "//intellij_platform_sdk:android-studio-beta": [":beta"],
            "//intellij_platform_sdk:android-studio-canary": [":canary"],
            "//intellij_platform_sdk:android-studio-latest-mac": [":stable"],
            "//intellij_platform_sdk:android-studio-beta-mac": [":beta"],
            "//intellij_platform_sdk:android-studio-canary-mac": [":canary"],
            "//intellij_platform_sdk:android-studio-oss-oldest-stable": [":stable"],
            "//intellij_platform_sdk:android-studio-oss-latest-stable": [":stable"],
            "//intellij_platform_sdk:android-studio-oss-under-dev": [":canary"],
        },
    ),
    outs = ["com/google/idea/common/util/channel/Channel.java"],
    cmd = "\n".join([
        "CHANNEL=$$(cat $<)",
        "cat << EOM > $@",
        "package com.google.idea.common.util.channel;",
        "",
        "public class Channel {",
        "  public static final String CHANNEL = \"$$CHANNEL\";",
        "}",
        "EOM",
    ]),
)
