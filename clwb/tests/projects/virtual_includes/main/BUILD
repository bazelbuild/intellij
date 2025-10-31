load("@rules_cc//cc:defs.bzl", "cc_binary")

cc_binary(
    name = "main",
    srcs = ["main.cc"],
    visibility = ["//visibility:public"],
    deps = [
        "//lib/cmake:lib",
        "//lib/impl_deps:lib",
        "//lib/raw_files:lib",
        "//lib/strip_absolut:gen",
        "//lib/strip_absolut:lib",
        "//lib/strip_relative:lib",
    ],
)

cc_binary(
    name = "raw",
    srcs = [
        "raw.cc",
        "//lib/raw_files:files",
    ],
    # basically manual strip absolut, different flags required for MSVC
    copts = select({
        "@platforms//os:windows": [
            "/Ilib/raw_files/default",
            "/external:Ilib/raw_files/system",
            "/external:Ilib/raw_files/quote",
        ],
        "//conditions:default": [
            "-Ilib/raw_files/default",
            "-isystemlib/raw_files/system",
            "-iquotelib/raw_files/quote",
        ],
    }),
)
