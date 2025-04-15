load("@rules_cc//cc:defs.bzl", "cc_binary")

cc_binary(
    name = "hello-world",
    srcs = ["hello-world.cc"],
    deps = [
        "//lib/impl_deps:lib",
        "//lib/strip_absolut:gen",
        "//lib/strip_absolut:lib",
        "//lib/strip_relative:lib",
    ],
)
