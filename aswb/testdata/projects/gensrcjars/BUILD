load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

test_project_package(
    name = "test_data",
    all_targets = [],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
