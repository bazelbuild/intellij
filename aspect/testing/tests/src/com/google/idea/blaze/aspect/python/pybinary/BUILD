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

py_binary(
    name = "buildfile_args",
    srcs = ["simple.py"],
    args = [
        "--ARG1",
        "--ARG2=$(COMPILATION_MODE)",
        "--ARG3='with spaces'",
    ],
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

intellij_aspect_test_fixture(
    name = "buildfile_args_fixture",
    deps = [
        ":buildfile_args",
    ],
)

java_test(
    name = "PyBinaryTest",
    srcs = ["PyBinaryTest.java"],
    data = [
        ":buildfile_args_fixture",
        ":simple3_fixture",
        ":simple_fixture",
    ],
    env = {
        "COMPILATION_MODE_FOR_TEST": "$(COMPILATION_MODE)",
    },
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
