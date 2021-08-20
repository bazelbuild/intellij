load(
    "//third_party/intellij/bazel/plugin/aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_test",
)

licenses(["notice"])  # Apache 2.0

scala_test(
    name = "FooTest",
    size = "large",
    srcs = ["FooTest.scala"],
)

intellij_aspect_test_fixture(
    name = "footest_fixture",
    deps = [":FooTest"],
)

java_test(
    name = "ScalaTestTest",
    srcs = ["ScalaTestTest.java"],
    data = [":footest_fixture"],
    deps = [
        "//intellij_platform_sdk:test_libs",
        "//third_party/intellij/bazel/plugin/aspect/testing:BazelIntellijAspectTest",
        "//third_party/intellij/bazel/plugin/aspect/testing:guava",
        "//third_party/intellij/bazel/plugin/aspect/testing/rules:IntellijAspectTest",
        "//third_party/intellij/bazel/plugin/aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//third_party/intellij/bazel/plugin/proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)
