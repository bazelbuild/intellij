load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

java_library(
    name = "simple",
    srcs = ["Foo.java"],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [":simple"],
)

java_test(
    name = "BuildFileTest",
    srcs = ["BuildFileTest.java"],
    data = [":simple_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
