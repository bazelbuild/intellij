load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(
    ":noop_analysis_test.bzl",
    "noop_analysis_test",
)

licenses(["notice"])

java_library(
    name = "foo",
    srcs = ["Foo.java"],
)

noop_analysis_test(
    name = "noop_analysis_test",
)

intellij_aspect_test_fixture(
    name = "analysis_test_fixture",
    deps = [
        ":foo",
        ":noop_analysis_test",
    ],
)

java_test(
    name = "AnalysisTestTest",
    srcs = ["AnalysisTestTest.java"],
    data = [":analysis_test_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "@junit_junit//jar",
    ],
)
