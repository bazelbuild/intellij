load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(default_visibility = ["//aswb/testdata/projects/sample1:__pkg__"])

test_project_package(
    name = "test_data",
    all_targets = [
        ":nested",
        ":nested2",
    ],
    visibility = ["//aswb/testdata/projects/sample1:__pkg__"],
)

java_library(
    name = "nested",
    srcs = glob(["**/NestedClass.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "nested2",
    srcs = glob(["**/NestedClass2.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@com_google_guava_guava//jar",
    ],
)
