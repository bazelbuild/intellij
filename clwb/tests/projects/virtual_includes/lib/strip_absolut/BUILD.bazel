load("@rules_cc//cc:defs.bzl", "cc_library")

cc_library(
    name = "lib",
    srcs = ["strip_absolut.cc"],
    hdrs = ["strip_absolut.h"],
    strip_include_prefix = "/lib",
    visibility = ["//visibility:public"]
)

genrule(
    name = "generate",
    outs = ["generated.h"],
    srcs = [],
    cmd = r"""echo '#define GENERATED_MACRO 0' > $@"""
)

cc_library(
    name = "gen",
    srcs = [],
    hdrs = ["generated.h"],
    strip_include_prefix = "/lib",
    visibility = ["//visibility:public"]
)
