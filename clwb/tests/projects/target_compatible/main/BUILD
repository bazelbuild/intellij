load("@rules_cc//cc:defs.bzl", "cc_binary")

cc_binary(
    name = "windows",
    srcs = ["windows.cc"],
    target_compatible_with = ["@platforms//os:windows"],
)

cc_binary(
    name = "macos",
    srcs = ["macos.cc"],
    target_compatible_with = ["@platforms//os:macos"],
)

cc_binary(
    name = "linux",
    srcs = ["linux.cc"],
    target_compatible_with = ["@platforms//os:linux"],
)
