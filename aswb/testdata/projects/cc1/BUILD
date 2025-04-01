load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//visibility:private"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":cc1",
    ],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

cc_library(
    name = "cc1",
    srcs = ["TestClass.cc"],
    hdrs = ["TestClass.h"],
    deps = [],
)
