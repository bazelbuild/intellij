licenses(["notice"])

load("@rules_java//java:defs.bzl", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

cc_binary(
    name = "simple_prefined",
    srcs = ["simple/simple.cc"],
    copts = [
        "-DPREFINED_BINDIR=$(BINDIR)",
        "-DPREFINED_BINDIR2=$$(BINDIR)",
    ],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [
        ":simple_prefined",
    ],
)

java_test(
    name = "CoptsMakeVarsTest",
    srcs = ["CoptsMakeVarsTest.java"],
    data = [":simple_fixture"],
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
