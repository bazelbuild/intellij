load("@rules_java//java:defs.bzl", "java_library", "java_test")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_channel")

package(default_visibility = ["//visibility:public"])

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
        "@error_prone_annotations//jar",
    ],
)

kt_jvm_library(
    name = "datafiles",
    srcs = ["src/com/google/idea/datafiles/Datafiles.kt"],
    deps = ["//intellij_platform_sdk:plugin_api"],
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
        "//third_party/java/junit",
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
    name = "freeform",
    outs = ["freeform.txt"],
    cmd = "echo freeform > $@",
)

genrule(
    name = "channel_java",
    srcs = select_for_channel({
        "stable": [":stable"],
        "beta": [":beta"],
        "canary": [":canary"],
        "freeform": [":freeform"],
    }),
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
