load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_library")
load(":foo_bar.bzl", "foo_bar_binary")

cc_binary(
    name = "simple",
    srcs = ["simple.cc"],
)

foo_bar_binary(
    name = "foo_bar",
    deps = ":simple",
)

cc_library(
    name = "shared_a",
    srcs = ["shared.cc"],
)

cc_library(
    name = "shared_b",
    srcs = ["shared.cc"],
)
