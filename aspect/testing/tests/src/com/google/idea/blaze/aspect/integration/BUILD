load("@build_bazel_integration_testing//tools:bazel_java_integration_test.bzl", "bazel_java_integration_test")

bazel_java_integration_test(
    name = "BazelInvokingIntegrationTest",
    srcs = ["BazelInvokingIntegrationTest.java"],
    data = [
        "//aspect:aspect_files",
    ],
    versions = [
        "0.28.1",
        "0.27.2",
    ],
)
