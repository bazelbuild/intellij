load("//build_defs:build_defs.bzl", "intellij_plugin_library")

package(default_visibility = ["//visibility:public"])

intellij_plugin_library(
    name = "plugin_library",
    deps = [
        "//skylark/src/com/google/idea/blaze/skylark/debugger",
        "//skylark/src/com/google/idea/blaze/skylark/repl",
    ],
)

test_suite(
    name = "integration_tests",
    tests = [
        "//skylark/test/integrationtests/com/google/idea/blaze/skylark/repl:SkylarkReplTest",
    ],
)
