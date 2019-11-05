load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])  # Apache 2.0

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

java_test(
    name = "JavaLibraryTest",
    srcs = ["JavaLibraryTest.java"],
    data = [":foo_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
