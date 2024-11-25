load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_test")

cc_binary(
    name = "hello-world",
    srcs = ["hello-world.cc"],
    visibility = ["//visibility:public"]
)

cc_test(
    name = "hello-test",
    size = "small",
    srcs = ["hello-test.cc"],
    deps = [
        "@googletest//:gtest",
        "@googletest//:gtest_main",
    ],
)
