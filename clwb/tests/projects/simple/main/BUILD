load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_test")

cc_binary(
    name = "hello-world",
    srcs = ["hello-world.cc"],
    copts = [
        "-Wall",
        "$(STACK_FRAME_UNLIMITED)",  # empty make variable should be stripped by make variable tokenization
    ],
)

cc_binary(
    name = "echo0",
    srcs = ["echo.cc"],
)

cc_binary(
    name = "echo1",
    srcs = ["echo.cc"],
    args = ["BUILD_FILE_STRING"],
)

cc_binary(
    name = "echo2",
    srcs = ["echo.cc"],
    args = ["$(location :echo.cc)"],
)

cc_test(
    name = "test",
    srcs = ["test.cc"],
    deps = ["@catch2//:catch2_main"],
)
