load("@io_bazel_rules_go//go:def.bzl", "go_library")
load("@io_bazel_rules_go//proto:def.bzl", "go_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

proto_library(
    name = "fooproto_proto",
    srcs = ["fooserver.proto"],
    visibility = ["//visibility:public"],
    deps = ["@protobuf//:empty_proto"],
)

go_proto_library(
    name = "fooproto_go_proto",
    compilers = ["@io_bazel_rules_go//proto:go_grpc"],
    importpath = "github.com/bazelbuild/intellij/examples/go/with_proto/proto",
    proto = ":fooproto_proto",
    visibility = ["//visibility:public"],
)

go_library(
    name = "proto",
    srcs = ["translators.go"],
    embed = [":fooproto_go_proto"],
    importpath = "github.com/bazelbuild/intellij/examples/go/with_proto/proto",
    visibility = ["//visibility:public"],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [":proto"],
)

java_test(
    name = "GoTest",
    srcs = ["GoTest.java"],
    data = [":simple_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "//third_party/java/junit",
    ],
)
