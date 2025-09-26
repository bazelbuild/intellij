load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
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

cc_binary(
    name = "empty_variable",
    srcs = ["simple/simple.cc"],
    copts = [
        "-Wall",
        "$(STACK_FRAME_UNLIMITED)",
    ],
)

cc_binary(
    name = "simple_make_var",
    srcs = ["simple/simple.cc"],
    copts = [
        '-DEXECPATH=\\"$(execpath simple/simple.cc)\\"',
        '-DROOTPATH=\\"$(rootpath simple/simple.cc)\\"',
        '-DRLOCATIONPATH=\\"$(rlocationpath simple/simple.cc)\\"',
        '-DLOCATION=\\"$(location simple/simple.cc)\\"',
    ],
)

intellij_aspect_test_fixture(
    name = "aspect_fixture",
    deps = [
        ":empty_variable",
        ":simple_make_var",
        ":simple_prefined",
    ],
)

java_test(
    name = "CoptsMakeVarsTest",
    srcs = ["CoptsMakeVarsTest.java"],
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
