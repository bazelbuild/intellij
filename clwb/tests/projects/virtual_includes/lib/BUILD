load("@rules_cc//cc:defs.bzl", "cc_library")

genrule(
    name = "generate",
    srcs = [],
    outs = ["generated.h"],
    cmd = r"""echo '#define GENERATED_MACRO 0' > $@""",
)

cc_library(
    name = "gen",
    srcs = [],
    hdrs = ["generated.h"],
    visibility = ["//visibility:public"],
)
