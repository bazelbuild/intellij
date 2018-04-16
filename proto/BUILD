#
# Description:
# Checked-in copy of the bazel proto deps.
#

licenses(["notice"])  # Apache 2.0

java_import(
    name = "proto_deps",
    jars = ["proto_deps.jar"],
    visibility = ["//visibility:public"],
)

proto_library(
    name = "intellij_ide_info_proto",
    srcs = ["intellij_ide_info.proto"],
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "intellij_ide_info_java_proto",
    visibility = ["//visibility:public"],
    deps = [":intellij_ide_info_proto"],
)

proto_library(
    name = "intellij_plugin_target_deploy_info_proto",
    srcs = ["intellij_plugin_target_deploy_info.proto"],
)

java_proto_library(
    name = "intellij_plugin_target_deploy_info_java_proto",
    deps = [":intellij_plugin_target_deploy_info_proto"],
)
