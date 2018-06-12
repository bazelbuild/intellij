licenses(["notice"])  # Apache 2.0

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

cc_library(
    name = "simple",
    srcs = ["simple.cc"],
    hdrs = ["simple.h"],
)

intellij_aspect_test_fixture(
    name = "fixture",
    deps = [":simple"],
)

java_test(
    name = "CcToolchainTest",
    srcs = ["CcToolchainTest.java"],
    data = [":fixture"],
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
