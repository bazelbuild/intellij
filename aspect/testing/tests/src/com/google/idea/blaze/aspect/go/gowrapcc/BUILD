load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

cc_library(
    name = "wrapped",
    srcs = ["wrapped.cc"],
)

go_wrap_cc(
    name = "simple",
    srcs = ["simple.swig"],
    deps = [":wrapped"],
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [":simple"],
)

java_test(
    name = "GoWrapCcTest",
    srcs = ["GoWrapCcTest.java"],
    data = [":simple_fixture"],
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
