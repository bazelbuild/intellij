load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

genrule(
    name = "gen_source",
    outs = ["Gen.java"],
    cmd = "echo 'package com.google.idea.blaze.aspect.general.artifacts;\nclass Gen {}' > $@",
)

java_library(
    name = "source",
    srcs = ["Foo.java"],
)

java_library(
    name = "gen",
    srcs = [":gen_source"],
)

intellij_aspect_test_fixture(
    name = "gen_sources_fixture",
    deps = [
        ":gen",
        ":source",
    ],
)

java_test(
    name = "ArtifactTest",
    srcs = ["ArtifactTest.java"],
    data = [
        ":gen_sources_fixture",
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
