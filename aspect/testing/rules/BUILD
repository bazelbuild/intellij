# Testing infrastructure for the plugin's Starlark aspect

load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

licenses(["notice"])

proto_library(
    name = "intellij_aspect_test_fixture_proto",
    srcs = ["intellij_aspect_test_fixture.proto"],
    deps = ["//proto:intellij_ide_info_proto"],
)

java_proto_library(
    name = "intellij_aspect_test_fixture_java_proto",
    visibility = ["//aspect/testing:__subpackages__"],
    deps = [":intellij_aspect_test_fixture_proto"],
)

java_binary(
    name = "IntellijAspectTestFixtureBuilder",
    testonly = True,
    srcs = ["src/com/google/idea/blaze/aspect/IntellijAspectTestFixtureBuilder.java"],
    visibility = ["//aspect/testing:__subpackages__"],
    deps = [
        ":intellij_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//proto:intellij_ide_info_java_proto",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "IntellijAspectTest",
    testonly = 1,
    srcs = ["src/com/google/idea/blaze/aspect/IntellijAspectTest.java"],
    visibility = ["//aspect/testing:__subpackages__"],
    deps = [
        ":intellij_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//intellij_platform_sdk:jsr305",
        "//proto:common_java_proto",
        "//proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)

kt_jvm_library(
    name = "IntellijAspectResource",
    testonly = 1,
    srcs = ["src/com/google/idea/blaze/aspect/IntellijAspectResource.kt"],
    visibility = ["//aspect/testing:__subpackages__"],
    deps = [
        ":intellij_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//intellij_platform_sdk:jsr305",
        "//proto:common_java_proto",
        "//proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
    ],
)
