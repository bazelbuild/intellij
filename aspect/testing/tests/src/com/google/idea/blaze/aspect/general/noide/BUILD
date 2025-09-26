load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_cc//cc:cc_library.bzl", "cc_library")
load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

cc_library(
    name = "foo",
    srcs = ["simple/simple.cc"],
)

cc_library(
    name = "bar",
    srcs = ["simple/simple.cc"],
    deps = [":foo"],
)

cc_library(
    name = "baz",
    srcs = ["simple/simple.cc"],
    tags = ["no-ide"],
    deps = [":bar"],
)

intellij_aspect_test_fixture(
    name = "noide_fixture",
    deps = [
        ":baz",
        ":foo",
    ],
)

java_test(
    name = "NoIdeTest",
    srcs = ["NoIdeTest.java"],
    data = [
        ":noide_fixture",
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
