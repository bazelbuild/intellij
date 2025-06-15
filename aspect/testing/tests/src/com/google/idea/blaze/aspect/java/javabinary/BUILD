load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")
load("//aspect/testing/rules:build_dependencies_blaze_deps_test_fixture.bzl", "build_dependencies_blaze_deps_test_fixture")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(":build_dependencies_blaze_deps_test.bzl", "test_suite")

licenses(["notice"])

java_library(
    name = "foolib",
    srcs = ["Foo.java"],
)

java_binary(
    name = "foo",
    srcs = ["FooMain.java"],
    main_class = "com.google.MyMainClass",
    deps = [":foolib"],
)

intellij_aspect_test_fixture(
    name = "foo_fixture",
    deps = [":foo"],
)

java_test(
    name = "JavaBinaryTest",
    srcs = ["JavaBinaryTest.java"],
    data = [":foo_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)

build_dependencies_blaze_deps_test_fixture(
    name = "foo_build_dependencies_blaze_deps_test_fixture",
    tags = ["manual"],
    deps = [":foo"],
)

test_suite(
    name = "javabinary_test",
    target_under_test = ":foo_build_dependencies_blaze_deps_test_fixture",
)
