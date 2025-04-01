load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(
    default_visibility = ["//aswb/testdata/projects/kotlin1:__pkg__"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":nested",
        ":nested2",
    ],
    visibility = ["//aswb/testdata/projects/kotlin1:__pkg__"],
)

kt_jvm_library(
    name = "nested",
    srcs = glob(["**/NestedClass.kt"]),
    visibility = ["//visibility:public"],
    deps = ["@com_google_guava_guava//jar"],
)

kt_jvm_library(
    name = "nested2",
    srcs = glob(["**/NestedClass2.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        "@com_google_guava_guava//jar",
    ],
)
