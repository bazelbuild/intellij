load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_library")

cc_library(
    name = "greeting_lib",
    srcs = ["greeting_lib.cc"],
    hdrs = ["greeting_lib.h"],
    visibility = ["//src:__pkg__"],
)

cc_test(
    name = "greeting_test",
    srcs = ["greeting_test.cc"],
    deps = [
        ":greeting_lib",
        "@com_google_googletest//:gtest_main",
    ],
)
