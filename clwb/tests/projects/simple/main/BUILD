load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_test")

cc_binary(
    name = "main_cc",
    srcs = ["main.cc"],
    conlyopts = ["-DCONLYOPTS"],
    copts = [
        "-Wall",
        "$(STACK_FRAME_UNLIMITED)",  # empty make variable should be stripped by make variable tokenization
    ],
    cxxopts = ["-DCXXOPTS"],
    defines = [
        'SPACE_DEFINE="1 2 3"',
        "SIMPLE_DEFINE=42",
    ],
)

cc_binary(
    name = "main_c",
    srcs = ["main.c"],
    conlyopts = ["-DCONLYOPTS"],
    copts = [
        "-Wall",
        "$(STACK_FRAME_UNLIMITED)",  # empty make variable should be stripped by make variable tokenization
    ],
    cxxopts = ["-DCXXOPTS"],
    defines = [
        'SPACE_DEFINE="1 2 3"',
        "SIMPLE_DEFINE=42",
    ],
)

cc_test(
    name = "test",
    srcs = ["test.cc"],
    deps = ["@catch2//:catch2_main"],
)
