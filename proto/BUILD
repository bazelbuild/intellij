#
# Description:
# Proto dependencies from bazel.
#

licenses(["notice"])  # Apache 2.0

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

# TODO(b/188322932): remove
# repackaging is no longer done here,
# remove all dependency on this target and add the actual dependencies instead
# delete this target after all dependencies on it have been removed
java_library(
    name = "proto_deps",
    visibility = ["//visibility:public"],
    exports = [
        ":common_java_proto",
        ":fast_build_info_java_proto",
        ":intellij_ide_info_java_proto",
        ":intellij_plugin_target_deploy_info_java_proto",
        ":project_data_java_proto",
        "//google/protobuf:duration_java_proto",
        "//third_party/bazel/src/main/java/com/google/devtools/build/lib/buildeventstream/proto:build_event_stream_java_proto",
        "//third_party/bazel/src/main/java/com/google/devtools/build/lib/starlarkdebug/proto:starlark_debugging_java_proto",
        "//third_party/bazel/src/main/protobuf:android_deploy_info_java_proto",
        "//third_party/bazel/src/main/protobuf:build_java_proto",
        "//third_party/bazel/src/main/protobuf:command_line_java_proto",
        "//third_party/bazel/src/main/protobuf:deps_java_proto",
        "@com_google_protobuf//:protobuf_java:protobuf_lite",
    ],
)
