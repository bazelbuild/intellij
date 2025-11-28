load("@rules_cc//cc:cc_library.bzl", "cc_library")

cc_library(
    name = "cc_externaldep",
    srcs = ["TestClass.cc"],
    hdrs = ["TestClass.h"],
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = ["@abseil-cpp//absl/strings"],
)
