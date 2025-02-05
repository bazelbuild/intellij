load("//testing:test_defs.bzl", "bazel_integration_tests")

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

bazel_integration_tests(
    name = "bazel_invocation_integration_test",
    test_runner = ":BazelInvokingIntegrationTestRunner",
    workspace_path = "testdata",
)
