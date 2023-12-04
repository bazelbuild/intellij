licenses(["notice"])

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

java_library(
    name = "foo",
    srcs = ["Foo.java"],
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
        "@junit_junit//jar",
    ],
)
