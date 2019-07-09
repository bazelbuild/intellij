load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

proto_library(
    name = "foo_proto",
    srcs = ["foo.proto"],
)

proto_library(
    name = "bar_proto",
    srcs = ["bar.proto"],
    deps = [":foo_proto"],
)

go_proto_library(
    name = "bar_go_proto",
    deps = [":bar_proto"],
)

proto_library(
    name = "baz",  # common but nonstandard name format
    srcs = ["baz.proto"],
)

go_proto_library(
    name = "baz_go_proto",
    deps = [":baz"],
)

go_library(
    name = "lib",
    srcs = ["lib.go"],
    deps = [
        ":bar_go_proto",
        ":baz_go_proto",
    ],
)

intellij_aspect_test_fixture(
    name = "lib_fixture",
    deps = [":lib"],
)

java_test(
    name = "GoProtoLibraryTest",
    srcs = ["GoProtoLibraryTest.java"],
    data = [":lib_fixture"],
    deps = [
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:truth",
        "//java/com/google/common/collect",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aspect:BlazeIntellijAspectTest",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
