load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_test")

cc_binary(
    name = "hello-world",
    srcs = ["hello-world.cc"],
    copts = [
        "-Wall",
        "$(STACK_FRAME_UNLIMITED)", # empty make variable should be stripped by make variable tokenization
    ],
)

cc_test(
    name = "test",
    srcs = ["test.cc"],
    deps = ["@catch2//:catch2_main"],
)
