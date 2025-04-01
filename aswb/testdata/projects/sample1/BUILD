load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(default_visibility = ["//visibility:private"])

test_project_package(
    name = "test_data",
    all_targets = [
        ":lib",
        ":sample1",
    ],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

java_library(
    name = "sample1",
    srcs = glob(["java/com/example/sample1/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//aswb/testdata/projects/sample1/java/com/example/sample1/nested",
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
    ],
)
