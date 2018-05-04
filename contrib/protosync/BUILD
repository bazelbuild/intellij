load("//build_defs:build_defs.bzl","intellij_plugin")

java_library(
    name = "upstream_plugin",
    neverlink = 1,
    exports = ["@protobuf_jetbrains_plugin//:protobuf_jetbrains_plugin"],
    visibility=["//visibility:private"]
)

java_library(
  name = "protosync",
  srcs = glob(["src/*.java"]),
  deps = [
      "//base",
      "//intellij_platform_sdk:plugin_api",
      ":upstream_plugin"
  ],
  visibility = ["//visibility:public"],
)

filegroup(
    name="plugin_xml",
    srcs =["proto-settings-sync.xml"],
    visibility = ["//visibility:public"]
)
