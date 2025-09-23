load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

cc_binary(
    name = "test",
    srcs = ["simple/simple.cc"],
    deps = [":alias"],
)

alias(
    name = "alias",
    actual = ":alias2",
)

alias(
    name = "alias2",
    actual = ":real",
)

cc_binary(
    name = "real",
    srcs = ["simple/simple.cc"],
)

intellij_aspect_test_fixture(
    name = "alias_fixture",
    deps = [":test"],
)

java_test(
    name = "AliasTest",
    srcs = ["AliasTest.java"],
    data = [":alias_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)
