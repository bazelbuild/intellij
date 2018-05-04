load("//build_defs:build_defs.bzl","merged_plugin_xml")

merged_plugin_xml(
    name = "plugin_xml",
    srcs = ["//contrib/protosync:plugin_xml"],
    visibility=["//visibility:public"]
)

java_library(
    name = "contrib",
    runtime_deps=["//contrib/protosync"],
    visibility=["//visibility:public"]
)