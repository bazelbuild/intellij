load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
)

licenses(["notice"])  # Apache 2.0

scala_library(
    name = "foolib",
    srcs = ["Foo.scala"],
)

scala_binary(
    name = "foo",
    srcs = ["FooMain.scala"],
    main_class = "com.google.MyMainClass",
    deps = [":foolib"],
)

intellij_aspect_test_fixture(
    name = "foo_fixture",
    deps = [":foo"],
)

java_test(
    name = "ScalaBinaryTest",
    srcs = ["ScalaBinaryTest.java"],
    data = [":foo_fixture"],
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
