licenses(["notice"])  # Apache 2.0

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

proto_library(
    name = "foo_proto",
    srcs = ["foo.proto"],
)

java_proto_library(
    name = "foo_java_proto",
    deps = [":foo_proto"],
)

intellij_aspect_test_fixture(
    name = "java_proto_library_fixture",
    deps = [":foo_java_proto"],
)

java_test(
    name = "JavaProtoLibraryTest",
    srcs = ["JavaProtoLibraryTest.java"],
    data = [
        ":java_proto_library_fixture",
    ],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
        "@truth//jar",
    ],
)
