load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

proto_library(
    name = "foo_proto",
    srcs = ["foo.proto"],
)

proto_library(
    name = "bar_proto",
    srcs = ["bar.proto"],
    deps = [":foo_proto"],
)

java_proto_library(
    name = "bar_java_proto",
    deps = [":bar_proto"],
)

java_library(
    name = "lib",
    srcs = ["Lib.java"],
    deps = [":bar_java_proto"],
)

intellij_aspect_test_fixture(
    name = "lib_fixture",
    deps = [":lib"],
)

java_test(
    name = "JavaProtoLibraryTest",
    srcs = ["JavaProtoLibraryTest.java"],
    data = [
        ":lib_fixture",
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
