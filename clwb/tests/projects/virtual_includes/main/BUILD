load("@rules_cc//cc:defs.bzl", "cc_binary")

cc_binary(
    name = "hello-world",
    srcs = [
        "hello-world.cc",
        "//lib/raw_files:lib",
    ],
    copts = [
        "-Ilib/raw_files/default",  # basically manual strip absolut
        "-isystemlib/raw_files/system",
        "-iquotelib/raw_files/quote",
    ],
    deps = [
        "//lib/impl_deps:lib",
        "//lib/strip_absolut:gen",
        "//lib/strip_absolut:lib",
        "//lib/strip_relative:lib",
    ],
)
