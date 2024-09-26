load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

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
        ":foo_exports_fixture",
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
