load("@rules_cc//cc:defs.bzl", "cc_binary")

cc_binary(
    name = "hello-world",
    srcs = [
        "hello-world.cc",
        "//lib/raw_files:lib",
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
    deps = [
        "//lib/impl_deps:lib",
        "//lib/strip_absolut:gen",
        "//lib/strip_absolut:lib",
        "//lib/strip_relative:lib",
    ],
)
