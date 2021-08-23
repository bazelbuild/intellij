load(
    "//third_party/intellij/bazel/plugin/aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_library",
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
        "//intellij_platform_sdk:test_libs",
        "//third_party/intellij/bazel/plugin/aspect/testing:BazelIntellijAspectTest",
        "//third_party/intellij/bazel/plugin/aspect/testing/rules:IntellijAspectTest",
        "//third_party/intellij/bazel/plugin/aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//third_party/intellij/bazel/plugin/proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)
