load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_library",
)
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])  # Apache 2.0

scala_library(
    name = "simple",
    srcs = ["Foo.scala"],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [":simple"],
)

java_test(
    name = "ScalaLibraryTest",
    srcs = ["ScalaLibraryTest.java"],
    data = [":simple_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
