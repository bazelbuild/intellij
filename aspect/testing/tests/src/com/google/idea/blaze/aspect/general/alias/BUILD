licenses(["notice"])

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

java_library(
    name = "test",
    srcs = ["Foo.java"],
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

java_library(
    name = "real",
    srcs = ["Foo.java"],
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
        "@junit//jar",
    ],
)
