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
    name = "global_toolchain_fixture",
    deps = [":simple"]
)

java_test(
    name = "KotlinGlobalToolchainTest",
    srcs = ["KotlinGlobalToolchainTest.java"],
    data = [
        ":global_toolchain_fixture",
        "@io_bazel_rules_kotlin//kotlin:kt_toolchain_ide_info.json"
    ],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
        "@truth//jar",
    ],
)