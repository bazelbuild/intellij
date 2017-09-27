licenses(["notice"])  # Apache 2.0

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

java_library(
    name = "source_only",
    srcs = ["Foo.java"],
)

genrule(
    name = "gen_sources",
    outs = ["Gen.java"],
    cmd = "echo 'class Gen {}' > $@",
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
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
        "@truth//jar",
    ],
)
