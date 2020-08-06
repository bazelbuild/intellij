load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])  # Apache 2.0

py_binary(
    name = "simple",
    srcs = ["simple.py"],
    python_version = "PY3",
)

py_binary(
    name = "simple3",
    srcs = ["simple.py"],
    main = "simple.py",
    python_version = "PY3",
)

intellij_aspect_test_fixture(
    name = "simple_fixture",
    deps = [
        ":simple",
    ],
)

intellij_aspect_test_fixture(
    name = "simple3_fixture",
    deps = [
        ":simple3",
    ],
)

java_test(
    name = "PyBinaryTest",
    srcs = ["PyBinaryTest.java"],
    data = [
        ":simple3_fixture",
        ":simple_fixture",
    ],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
