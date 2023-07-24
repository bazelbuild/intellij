load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_test")

cc_binary(
    name = "hello_world",
    srcs = ["hello_world.cc"],
    deps = [
        "//src/lib:greeting_lib",
    ],
)

cc_test(
    name = "hello_world_test",
    srcs = ["hello_world.cc"],
    deps = [
        "//src/lib:greeting_lib",
    ],
)
