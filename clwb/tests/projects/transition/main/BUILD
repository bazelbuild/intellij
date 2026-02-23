load("@rules_cc//cc:defs.bzl", "cc_binary")
load(":foo_bar.bzl", "foo_bar_binary")

cc_binary(
    name = "simple",
    srcs = ["simple.cc"],
)

foo_bar_binary(
    name = "foo_bar",
    deps = ":simple",
)
