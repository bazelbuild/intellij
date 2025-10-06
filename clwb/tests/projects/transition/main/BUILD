load(":multi_arch_binary.bzl", "multi_arch_binary")

package(default_visibility = ["//visibility:public"])

cc_binary(
    name = "main",
    srcs = ["main.cc"],
)

multi_arch_binary(
    name = "multi_arch_main",
    deps = ":main",
    out_amd64 = "main_linux_amd64",
    out_arm64  = "main_linux_arm64",
)

