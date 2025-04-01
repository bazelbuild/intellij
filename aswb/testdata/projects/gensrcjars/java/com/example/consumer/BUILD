load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//aswb/testdata/projects/gensrcjars:__subpackages__"],
)

java_library(
    name = "consumer",
    srcs = glob(["*.java"]),
    deps = [
        "//aswb/testdata/projects/gensrcjars/java/com/example/jar1",
        "//aswb/testdata/projects/gensrcjars/java/com/example/jar2",
        "@com_google_guava_guava//jar",
    ],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":consumer",
    ],
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//aswb/testdata/projects/gensrcjars:__subpackages__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
