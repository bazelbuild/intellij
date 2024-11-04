load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

java_library(
    name = "project",
    srcs = ["java/com/example/project/Class1.java"],
    deps = [
        "//aswb/testdata/projects/deps/external",
        "//aswb/testdata/projects/deps/external:external_gensrcjar",
        "//aswb/testdata/projects/deps/external:external_srcjar",
    ],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":project",
    ],
    external_sources = ["//aswb/testdata/projects/deps/external:sources"],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
