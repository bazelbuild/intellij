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
    name = "common_proto",
    srcs = ["common.proto"],
    visibility = ["//visibility:private"],
)

java_proto_library(
    name = "common_java_proto",
    visibility = ["//visibility:public"],
    deps = [":common_proto"],
)

proto_library(
    name = "fast_build_info_proto",
    srcs = ["fast_build_info.proto"],
    visibility = ["//visibility:public"],
    deps = [":common_proto"],
)

java_proto_library(
    name = "fast_build_info_java_proto",
    visibility = ["//visibility:public"],
    deps = [":fast_build_info_proto"],
)

proto_library(
    name = "intellij_ide_info_proto",
    srcs = ["intellij_ide_info.proto"],
    visibility = ["//visibility:public"],
    deps = [
        ":common_proto",
    ],
)

java_proto_library(
    name = "intellij_ide_info_java_proto",
    visibility = ["//visibility:public"],
    deps = [":intellij_ide_info_proto"],
)

proto_library(
    name = "project_data_proto",
    srcs = ["project_data.proto"],
    deps = [
        ":common_proto",
        ":intellij_ide_info_proto",
        "//third_party/bazel/src/main/protobuf:build_proto",
    ],
)

java_proto_library(
    name = "project_data_java_proto",
    deps = [":project_data_proto"],
)

proto_library(
    name = "intellij_plugin_target_deploy_info_proto",
    srcs = ["intellij_plugin_target_deploy_info.proto"],
)

java_proto_library(
    name = "intellij_plugin_target_deploy_info_java_proto",
    deps = [":intellij_plugin_target_deploy_info_proto"],
)
