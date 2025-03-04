load("@bazel_binaries//:defs.bzl", "bazel_binaries")
load("@rules_bazel_integration_test//bazel_integration_test:defs.bzl", "bazel_integration_test")
load("@rules_java//java:defs.bzl", "java_binary")

java_binary(
    name = "BazelInvokingIntegrationTestRunner",
    testonly = True,
    srcs = ["BazelInvokingIntegrationTestRunner.java"],
    main_class = "com.google.idea.blaze.aspect.integration.BazelInvokingIntegrationTestRunner",
    deps = [
        "//aspect/testing:guava",
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//sdkcompat",
    ],
)

bazel_integration_test(
    name = "bazel_invocation_integration_test",
    bazel_version = bazel_binaries.versions.current,
    env = {"BIT_BAZEL_VERSION": bazel_binaries.versions.current},
    tags = ["exclusive"],
    test_runner = ":BazelInvokingIntegrationTestRunner",
    workspace_path = "testdata",
)
