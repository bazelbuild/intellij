load("@rules_java//java:defs.bzl", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

java_library(
    name = "foo",
    srcs = ["testsrc/Foo.java"],
)

java_library(
    name = "single_dep",
    srcs = ["testsrc/SingleDep.java"],
    deps = [":foo"],
)

intellij_aspect_test_fixture(
    name = "single_dep_fixture",
    deps = [":single_dep"],
)

java_library(
    name = "transitive_dep",
    srcs = ["testsrc/TransitiveDep.java"],
    deps = [":single_dep"],
)

intellij_aspect_test_fixture(
    name = "transitive_dep_fixture",
    deps = [":transitive_dep"],
)

java_library(
    name = "single_dep_sibling",
    srcs = ["testsrc/SingleDepSibling.java"],
    deps = [":foo"],
)

java_library(
    name = "diamond_dep",
    srcs = ["testsrc/DiamondDep.java"],
    deps = [
        ":single_dep",
        ":single_dep_sibling",
    ],
)

intellij_aspect_test_fixture(
    name = "diamond_dep_fixture",
    deps = ["diamond_dep"],
)

java_library(
    name = "foo_exporter",
    srcs = ["testsrc/FooExporter.java"],
    exports = [":foo"],
)

java_library(
    name = "export_consumer",
    srcs = ["testsrc/ExportConsumer.java"],
    deps = [":foo_exporter"],
)

intellij_aspect_test_fixture(
    name = "export_fixture",
    deps = [":export_consumer"],
)

java_library(
    name = "foo_exporter_exporter",
    srcs = ["testsrc/FooExporterExporter.java"],
    exports = [":foo_exporter"],
)

java_library(
    name = "transitive_export_consumer",
    srcs = ["testsrc/TransitiveExportConsumer.java"],
    deps = [":foo_exporter_exporter"],
)

intellij_aspect_test_fixture(
    name = "transitive_export_fixture",
    deps = [":transitive_export_consumer"],
)

java_library(
    name = "runtime_dep_lib",
    runtime_deps = [":foo"],
)

intellij_aspect_test_fixture(
    name = "runtime_dep_lib_fixture",
    deps = [":runtime_dep_lib"],
)

java_test(
    name = "DependenciesTest",
    srcs = ["DependenciesTest.java"],
    data = [
        ":diamond_dep_fixture",
        ":export_fixture",
        ":runtime_dep_lib_fixture",
        ":single_dep_fixture",
        ":transitive_dep_fixture",
        ":transitive_export_fixture",
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
