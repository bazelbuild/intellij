load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_test")

cc_binary(
    name = "main",
    srcs = ["main.cc"],
    deps = [
        "//proto:addressbook_cc_proto",
        "@protobuf//:protobuf",
        "@protobuf//src/google/protobuf/util:time_util",
    ],
)
