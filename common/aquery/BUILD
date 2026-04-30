load("//build_defs:intellij_plugin_library.bzl", "intellij_plugin_library")
load("//build_defs:intellij_unit_test.bzl", "intellij_unit_test")

intellij_plugin_library(
    name = "action_graph",
    srcs = ["ActionGraph.kt"],
    visibility = ["//visibility:public"],
    deps = ["//third_party/bazel/src/main/protobuf:analysis_v2_java_proto"],
)

intellij_unit_test(
    data = [":fixture.textproto"],
    test = "ActionGraphTest.kt",
    test_package = "com.google.idea.common.aquery",
    deps = [
        ":action_graph",
        "//testing:runfiles",
        "//third_party/bazel/src/main/protobuf:analysis_v2_java_proto",
        "@protobuf//:protobuf_java",
    ],
)
