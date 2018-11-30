licenses(["notice"])  # Apache 2.0

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(
    "@io_bazel_rules_scala//twitter_scrooge:twitter_scrooge.bzl",
    "scrooge_scala_library",
)

scrooge_scala_library(
    name = "simple",
    visibility = ["//visibility:public"],
    deps = ["//aspect/testing/tests/src/com/google/idea/blaze/aspect/scala/scroogescalalibrary/thrift:thrift"],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [":simple"],
)

java_test(
    name = "ScroogeScalaLibraryTest",
    srcs = ["ScroogeScalaLibraryTest.java"],
    data = [":simple_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
        "@truth//jar",
        ":simple",
    ],
)
