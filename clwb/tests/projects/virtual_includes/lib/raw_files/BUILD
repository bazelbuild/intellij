load("@rules_cc//cc:defs.bzl", "cc_library")

filegroup(
    name = "files",
    srcs = glob(["**/*.h"]),
    visibility = ["//visibility:public"],
)

cc_library(
    name = "lib",
    hdrs = [":files"],
    includes = [
        "default",
        "quote",
        "system",
    ],
    visibility = ["//visibility:public"],
)
