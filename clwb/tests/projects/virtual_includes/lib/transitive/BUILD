load("@rules_cc//cc:defs.bzl", "cc_library")

genrule(
    name = "generate",
    srcs = [],
    outs = ["generated.h"],
    cmd = r"""echo '#define LIB_GENERATED_MACRO 1' > $@""",
)

cc_library(
    name = "transitive",
    srcs = [],
    hdrs = ["generated.h"],
    visibility = ["//visibility:public"],
)