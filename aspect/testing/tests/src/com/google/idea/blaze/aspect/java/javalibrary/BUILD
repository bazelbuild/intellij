load("@rules_java//java:defs.bzl", "java_library", "java_test")
load("//aspect/testing/rules:build_dependencies_blaze_deps_test_fixture.bzl", "build_dependencies_blaze_deps_test_fixture")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(":build_dependencies_blaze_deps_test.bzl", "test_suite")

licenses(["notice"])

java_library(
    name = "foo",
    srcs = ["Foo.java"],
    deps = [":direct"],
)

java_library(
    name = "direct",
    srcs = ["Direct.java"],
    deps = [":indirect"],
)

java_library(
    name = "indirect",
    srcs = ["Indirect.java"],
    deps = [":distant"],
)

java_library(
    name = "distant",
    srcs = ["Distant.java"],
)

intellij_aspect_test_fixture(
    name = "foo_fixture",
    deps = [":foo"],
)

java_library(
    name = "foo_exports",
    srcs = ["Foo.java"],
    deps = [":exports_direct"],
)

java_library(
    name = "exports_direct",
    srcs = [],
    exports = [
        ":direct",
        ":exports_indirect",
    ],
)

java_library(
    name = "exports_indirect",
    srcs = [],
    exports = [":indirect"],
)

intellij_aspect_test_fixture(
    name = "foo_exports_fixture",
    deps = [":foo_exports"],
)

java_test(
    name = "JavaLibraryTest",
    srcs = ["JavaLibraryTest.java"],
    data = [
        "foo_exports_fixture",
        ":foo_fixture",
    ],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
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
    name = "javalibrary_test",
    target_under_test = ":foo_build_dependencies_blaze_deps_test_fixture",
)
