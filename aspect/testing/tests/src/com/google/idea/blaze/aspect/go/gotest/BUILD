load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

genrule(
    name = "simple_go",
    srcs = ["simple.go.testdata"],
    outs = ["simple.go"],
    cmd = "cp \"$<\" \"$@\"",
)

go_library(
    name = "simple",
    srcs = ["simple.go"],
)

genrule(
    name = "simple_test_go",
    srcs = ["simple_test.go.testdata"],
    outs = ["simple_test.go"],
    cmd = "cp \"$<\" \"$@\"",
)

go_test(
    name = "simple_test",
    srcs = ["simple_test.go"],
    library = ":simple",
)

genrule(
    name = "simple_no_library_test_go",
    srcs = ["simple_no_library_test.go.testdata"],
    outs = ["simple_no_library_test.go"],
    cmd = "cp \"$<\" \"$@\"",
)

go_test(
    name = "simple_no_library_test",
    srcs = ["simple_no_library_test.go"],
)

genrule(
    name = "simple_no_ide_go",
    srcs = ["simple_no_ide.go.testdata"],
    outs = ["simple_no_ide.go"],
    cmd = "cp \"$<\" \"$@\"",
)

go_library(
    name = "simple_no_ide",
    srcs = ["simple_no_ide.go"],
    tags = ["no-ide"],
)

genrule(
    name = "simple_no_ide_test_go",
    srcs = ["simple_no_ide_test.go.testdata"],
    outs = ["simple_no_ide_test.go"],
    cmd = "cp \"$<\" \"$@\"",
)

go_test(
    name = "simple_no_ide_test",
    srcs = ["simple_no_ide_test.go"],
    library = ":simple_no_ide",
)

intellij_aspect_test_fixture(
    name = "simple_test_fixture",
    deps = [
        ":simple_no_ide_test",
        ":simple_no_library_test",
        ":simple_test",
    ],
)

java_test(
    name = "GoTestTest",
    srcs = ["GoTestTest.java"],
    data = [":simple_test_fixture"],
    deps = [
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:truth",
        "//java/com/google/common/collect",
        "//javatests/com/google/devtools/intellij/blaze/plugin/aspect:BlazeIntellijAspectTest",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
