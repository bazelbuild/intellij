load("//testing/bazel:project_archive.bzl", "project_archive")

package(default_visibility = ["//clwb:__pkg__"])

filegroup(
    name = "simple_project",
    srcs = glob(["simple_project/**/*"]),
)

project_archive(
    name = "simple_project_archive",
    srcs = glob(["simple_project/**"]),
    strip_prefix = "simple_project",
    visibility = ["//visibility:public"],
)
