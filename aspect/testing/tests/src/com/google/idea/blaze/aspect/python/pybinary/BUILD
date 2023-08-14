load("@rules_python//python:defs.bzl", "py_binary")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

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

py_binary(
    name = "expand_datadeps",
    srcs = ["simple.py"],
    args = [
        "--ARG1=$(location :datadepfile)",
    ],
    data = [":datadepfile"],
    main = "simple.py",
    python_version = "PY3",
)

genrule(
    name = "datadepfile",
    outs = ["datadepfile.txt"],
    cmd = "touch $@",
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

intellij_aspect_test_fixture(
    name = "expand_datadeps_fixture",
    deps = [
        ":expand_datadeps",
    ],
)

java_test(
    name = "PyBinaryTest",
    srcs = ["PyBinaryTest.java"],
    data = [
        ":buildfile_args_fixture",
        ":expand_datadeps_fixture",
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
