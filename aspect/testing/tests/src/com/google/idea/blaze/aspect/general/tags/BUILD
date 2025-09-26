load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

cc_binary(
    name = "foo",
    srcs = ["simple/simple.cc"],
    tags = [
        "a",
        "b",
        "c",
        "d",
    ],
)

intellij_aspect_test_fixture(
    name = "tags_fixture",
    deps = [":foo"],
)

java_test(
    name = "TagTest",
    srcs = ["TagTest.java"],
    data = [
        ":tags_fixture",
    ],
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
