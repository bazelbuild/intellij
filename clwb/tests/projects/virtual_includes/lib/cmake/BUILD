load("@rules_foreign_cc//foreign_cc:defs.bzl", "cmake")

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)

cmake(
    name = "lib",
    lib_source = ":srcs",
    out_include_dir = "include",
    out_static_libs = select({
        "@platforms//os:windows": ["cmake.lib"],
        "//conditions:default": ["libcmake.a"],
    }),
    visibility = ["//visibility:public"],
)
