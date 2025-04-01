load("@rules_cc//cc:defs.bzl", "cc_library")

cc_library(
    name = "lib",
    srcs = ["impl.cc"],
    hdrs = ["impl.h"],
    implementation_deps = ["//lib/strip_relative:lib"],
    visibility = ["//visibility:public"],
)
