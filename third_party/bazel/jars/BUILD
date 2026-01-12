load("//build_defs:intellij_plugin_library.bzl", "intellij_plugin_library")

package(default_visibility = ["//visibility:public"])

intellij_plugin_library(
    name = "starlark_repl",
    data = {"bazel/starlark_repl.jar": ":starlark_deploy.jar"},
)

intellij_plugin_library(
    name = "execlog_parser",
    data = {"bazel/execlog_parser.jar": ":parser_deploy.jar"},
)
