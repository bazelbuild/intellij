licenses(["notice"])

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

java_plugin(
    name = "plugin",
    srcs = ["Plugin.java"],
    processor_class = "com.google.devtools.intellij.blaze.plugin.aspect.tests.java.javaplugin.Plugin",
)

intellij_aspect_test_fixture(
    name = "java_plugin_fixture",
    deps = [":plugin"],
)

java_test(
    name = "JavaPluginTest",
    srcs = ["JavaPluginTest.java"],
    data = [":java_plugin_fixture"],
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
