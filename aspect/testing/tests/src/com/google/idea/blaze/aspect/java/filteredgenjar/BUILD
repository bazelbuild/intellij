load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

java_library(
    name = "source_only",
    srcs = ["Foo.java"],
)

genrule(
    name = "gen_sources",
    outs = ["Gen.java"],
    cmd = "echo 'package com.google.idea.blaze.aspect.java.filteredgenjar;\nclass Gen {}' > $@",
)

java_library(
    name = "gen_only",
    srcs = [":gen_sources"],
)

java_library(
    name = "mixed",
    srcs = [
        "Foo.java",
        ":gen_sources",
    ],
)

intellij_aspect_test_fixture(
    name = "source_only_fixture",
    deps = [":source_only"],
)

intellij_aspect_test_fixture(
    name = "gen_only_fixture",
    deps = [":gen_only"],
)

intellij_aspect_test_fixture(
    name = "mixed_fixture",
    deps = [":mixed"],
)

java_test(
    name = "FilteredGenJarTest",
    srcs = ["FilteredGenJarTest.java"],
    data = [
        ":gen_only_fixture",
        ":mixed_fixture",
        ":source_only_fixture",
    ],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)
