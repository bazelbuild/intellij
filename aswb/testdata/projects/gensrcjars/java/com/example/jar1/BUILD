load(
    "//aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)
load(
    "//aswb/testdata/projects/gensrcjars:gen_srcjar.bzl",
    "gen_srcjar",
)

package(
    default_visibility = ["//aswb/testdata/projects/gensrcjars:__subpackages__"],
)

targets = gen_srcjar(
    name = "jar1",
    src = "Jar1Class.java_",
    package_dir = "com/example/jar1",
)

test_project_package(
    name = "test_data",
    all_targets = targets,
    visibility = [
        "//aswb/testdata/projects:__pkg__",
        "//aswb/testdata/projects/gensrcjars:__subpackages__",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)
