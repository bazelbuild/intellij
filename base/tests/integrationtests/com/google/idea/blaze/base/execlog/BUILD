load("//build_defs:intellij_integration_test.bzl", "intellij_integration_test")

intellij_integration_test(
    data = ["fixture.log"],
    env = {"FIXTURE_LOG": "$(rlocationpath fixture.log)"},
    test = "ExeclogParserTest.kt",
    deps = [
        "//base/src/com/google/idea/blaze/base/execlog",
        "//common/util:runjar",
        "@bazel_tools//tools/java/runfiles",
    ],
)
