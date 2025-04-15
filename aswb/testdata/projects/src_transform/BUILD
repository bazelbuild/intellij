load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_java//java:java_library.bzl", "java_library")
load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(default_visibility = ["//visibility:private"])

test_project_package(
    name = "test_data",
    all_targets = [
        ":src_transform",
        ":transformed_source",
    ],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

java_library(
    name = "src_transform",
    srcs = [":transformed_source"],
    visibility = ["//visibility:public"],
    deps = [
        "@com_google_guava_guava//jar",
    ],
)

copy_file(
    name = "transformed_source",
    src = "java/com/example/TestClass.java",
    out = "gen/com/example/TestClass.java",
    tags = ["ij-ignore-source-transform"],
)
