load("@aspect_bazel_lib//lib:transitions.bzl", "platform_transition_binary")
load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_library")

cc_library(
    name = "lib",
    srcs = ["srcs/lib.c"],
    hdrs = ["srcs/lib.h"],
    target_compatible_with = [
        "@platforms//os:none",
        "@platforms//cpu:armv8-m",
    ],
)

cc_binary(
    name = "main",
    srcs = ["srcs/main.c"],
    linkstatic = 1,
    target_compatible_with = [
        "@platforms//os:none",
        "@platforms//cpu:armv8-m",
    ],
    deps = [":lib"],
)

platform_transition_binary(
    name = "main_u575",
    binary = ":main",
    target_platform = "//platform:u575",
)
