licenses(["notice"])

load("@rules_java//java:defs.bzl", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

cc_library(
    name = "simple",
    srcs = ["simple/simple.cc"],
    hdrs = ["simple/simple.h"],
    copts = [
        "-DGOPT",
        "-Ifoo/baz/",
        "-I",
        "other/headers",
    ],
    defines = ["VERSION2"],
    includes = ["foo/bar"],
    textual_hdrs = ["simple/simple_textual.h"],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [":simple"],
)

cc_library(
    name = "lib2",
    copts = [
        "-v23",
        "-DDEV",
    ],
    defines = ["COMPLEX_IMPL"],
    includes = ["baz/lib"],
)

cc_library(
    name = "lib1",
    copts = [
        "-DGOPT",
        "-Ifoo/baz/",
    ],
    defines = ["VERSION2"],
    includes = ["foo/bar"],
    deps = [":lib2"],
)

intellij_aspect_test_fixture(
    name = "dep_fixture",
    deps = [":lib1"],
)

java_test(
    name = "CcLibraryTest",
    srcs = ["CcLibraryTest.java"],
    data = [
        ":dep_fixture",
        ":simple_fixture",
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
