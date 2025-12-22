load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_java//java:defs.bzl", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(":foo_bar.bzl", "foo_bar_binary")

cc_binary(
    name = "simple",
    srcs = ["simple/simple.cc"],
)

foo_bar_binary(
    name = "foo_bar",
    deps = ":simple",
)

intellij_aspect_test_fixture(
    name = "aspect_fixture",
    deps = [":foo_bar"],
)

java_test(
    name = "TransitionTest",
    srcs = ["TransitionTest.java"],
    data = [":aspect_fixture"],
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
