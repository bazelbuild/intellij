load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(
    default_visibility = ["//visibility:private"],
)

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

kt_jvm_library(
    name = "sample1",
    srcs = glob(["java/com/example/sample1/**/*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//aswb/testdata/projects/kotlin1/java/com/example/sample1/nested",
        "@com_google_guava_guava//jar",
    ],
)

kt_jvm_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/**/*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        "@com_google_guava_guava//jar",
    ],
)
