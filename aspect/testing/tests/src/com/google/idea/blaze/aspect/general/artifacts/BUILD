load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

genrule(
    name = "gen_source",
    outs = ["gen.cc"],
    cmd = "echo 'int main() { return 0; }' > $@",
)

cc_binary(
    name = "source",
    srcs = ["simple/simple.cc"],
)

cc_binary(
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
        "//third_party/java/junit",
    ],
)
