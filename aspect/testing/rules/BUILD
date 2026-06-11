# Testing infrastructure for the plugin's Starlark aspect

load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")

licenses(["notice"])

proto_library(
    name = "intellij_aspect_test_fixture_proto",
    srcs = ["intellij_aspect_test_fixture.proto"],
    deps = ["@intellij_aspect_sdk//sdk/proto:ide_info_proto"],
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
        "//proto:proto_deps",
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
        "//proto:proto_deps",
        "//third_party/java/junit",
    ],
)
