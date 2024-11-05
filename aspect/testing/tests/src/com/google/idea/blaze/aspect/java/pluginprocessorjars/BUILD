load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

java_library(
    name = "no_plugin",
    srcs = ["Foo.java"],
)

java_library(
    name = "has_plugin",
    srcs = ["Foo.java"],
    plugins = ["//third_party/java/auto_value:autovalue-plugin"],
)

java_library(
    name = "has_plugin_deps",
    srcs = ["Foo.java"],
    deps = ["//third_party/java/auto_value"],
)

intellij_aspect_test_fixture(
    name = "no_plugin_fixture",
    deps = [
        ":no_plugin",
    ],
)

intellij_aspect_test_fixture(
    name = "has_plugin_fixture",
    deps = [
        ":has_plugin",
    ],
)

intellij_aspect_test_fixture(
    name = "has_plugin_deps_fixture",
    deps = [
        ":has_plugin_deps",
    ],
)

java_test(
    name = "BazelPluginProcessorJarTest",
    srcs = ["BazelPluginProcessorJarTest.java"],
    data = [
        ":has_plugin_deps_fixture",
        ":has_plugin_fixture",
        ":no_plugin_fixture",
    ],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)
