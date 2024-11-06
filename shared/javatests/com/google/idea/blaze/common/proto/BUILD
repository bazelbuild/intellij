load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_test")
load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(
    default_visibility = DEFAULT_TEST_VISIBILITY,
)

proto_library(
    name = "test_proto",
    srcs = ["test_message.proto"],
)

java_proto_library(
    name = "test_java_proto",
    deps = [":test_proto"],
)

java_test(
    name = "ProtoStringInternerTest",
    size = "small",
    srcs = ["ProtoStringInternerTest.java"],
    deps = [
        ":test_java_proto",
        "//shared/java/com/google/idea/blaze/common/proto",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@protobuf//:protobuf_java",
        "@truth//jar",
    ],
)
