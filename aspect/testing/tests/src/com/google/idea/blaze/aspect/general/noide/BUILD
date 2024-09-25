load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

java_library(
    name = "foo",
    srcs = ["Foo.java"],
)

java_library(
    name = "bar",
    srcs = ["Foo.java"],
    deps = [":foo"],
)

java_library(
    name = "baz",
    srcs = ["Foo.java"],
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
    data = [":noide_fixture"],
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
