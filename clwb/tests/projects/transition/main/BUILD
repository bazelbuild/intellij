load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_library")
load(":copt_transition.bzl", "copt_transition_binary")

cc_binary(
    name = "simple",
    srcs = ["simple.cc"],
)

copt_transition_binary(
    name = "simple_bar",
    binary = ":simple",
    copts = ["-DBAR"],
)

copt_transition_binary(
    name = "simple_foo",
    binary = ":simple",
    copts = ["-DFOO"],
)

cc_library(
    name = "shared_a",
    srcs = ["shared.cc"],
)

cc_library(
    name = "shared_b",
    srcs = ["shared.cc"],
)
