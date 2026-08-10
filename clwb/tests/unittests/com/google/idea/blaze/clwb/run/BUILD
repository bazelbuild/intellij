load("//build_defs:intellij_unit_test.bzl", "intellij_unit_test")

intellij_unit_test(
    test = "DebugInfoCheckTest.kt",
    test_package = "com.google.idea.blaze.clwb.run",
    deps = [
        "//base",
        "//clwb:clwb_lib",
        "//common/aquery:action_graph",
        "//third_party/bazel/src/main/protobuf:analysis_v2_java_proto",
    ],
)
