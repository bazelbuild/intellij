load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_library")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

kt_jvm_library(
    name = "simple",
    srcs = [":Foo.kt"]
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [":simple"]
)

java_test(
    name = "KotlinLibraryTest",
    srcs = ["KotlinLibraryTest.java"],
    data = [":simple_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
        "@truth//jar",
    ],
)
