load("@rules_cc//cc:defs.bzl", "cc_binary")

cc_binary(
    name = "main",
    srcs = ["main.cc"],
    deps = [
        "//local_proto:animal_cc_proto",
        "@external_proto//:person_cc_proto",
        "@protobuf//:protobuf",
        "@protobuf//src/google/protobuf/util:time_util",
    ],
)
