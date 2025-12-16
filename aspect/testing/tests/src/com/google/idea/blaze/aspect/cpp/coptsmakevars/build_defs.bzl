load("@rules_cc//cc:cc_binary.bzl", "cc_binary")

def cc_binary_with_copts(name, all_copts, **kwargs):
    cc_binary(
        name = name,
        copts = all_copts,
        conlyopts = all_copts,
        cxxopts = all_copts,
        **kwargs
    )
