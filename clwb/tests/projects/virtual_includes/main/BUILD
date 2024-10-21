load("@rules_cc//cc:defs.bzl", "cc_binary")

cc_binary(
    name = "hello-world",
    srcs = ["hello-world.cc"],
    deps = [
        "//lib/strip_absolut:lib",
        "//lib/strip_absolut:gen",
        "//lib/strip_relative:lib",
        "//lib/impl_deps:lib",
    ]
)
