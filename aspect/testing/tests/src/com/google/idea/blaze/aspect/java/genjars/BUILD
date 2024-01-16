load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

java_library(
    name = "no_plugin",
    srcs = ["Foo.java"],
)

intellij_aspect_test_fixture(
    name = "no_plugin_fixture",
    deps = [":no_plugin"],
)

java_library(
    name = "has_plugin",
    srcs = ["Foo.java"],
    deps = ["//third_party/auto_value"],
)

intellij_aspect_test_fixture(
    name = "has_plugin_fixture",
    deps = [":has_plugin"],
)

java_test(
    name = "GenJarsTest",
    srcs = ["GenJarsTest.java"],
    data = [
        ":has_plugin_fixture",
        ":no_plugin_fixture",
    ],
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
