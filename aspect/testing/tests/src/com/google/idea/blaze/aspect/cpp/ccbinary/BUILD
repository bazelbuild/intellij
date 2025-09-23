load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_java//java:defs.bzl", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

cc_binary(
    name = "simple",
    srcs = ["simple/simple.cc"],
)

cc_binary(
    name = "expand_datadeps",
    srcs = ["simple/simple.cc"],
    args = [
        "--ARG1=$(location :datadepfile)",
    ],
    data = [":datadepfile"],
)

genrule(
    name = "datadepfile",
    outs = ["datadepfile.txt"],
    cmd = "touch $@",
)

intellij_aspect_test_fixture(
    name = "aspect_fixture",
    deps = [
        ":simple",
        ":expand_datadeps",
    ],
)

java_test(
    name = "CcBinaryTest",
    srcs = ["CcBinaryTest.java"],
    data = [":aspect_fixture"],
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
