#
# Description: Builds otel plugin
#

load("@rules_java//java:defs.bzl", "java_library")
load("//:version.bzl", "VERSION")
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "plugin_deploy_zip",
    "repackaged_files",
    "stamped_plugin_xml",
)

licenses(["notice"])

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/plugin.xml"],
    deps = [":otel_lib"],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    plugin_id = "com.snowflake.telemetry",
    plugin_name = "OTEL exporter extension",
    since_build_numbers = {"212": "212.5080.55"},
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

java_library(
    name = "otel_lib",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//otel/libs:opentelemetry-exporter-libs",
        "@maven//:io_grpc_grpc_stub",
        "@maven//:io_grpc_grpc_api",
        "@maven//:io_grpc_grpc_core",
        "@maven//:io_grpc_grpc_netty",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
    ],
)

intellij_plugin(
    name = "otel_bazel",
    plugin_xml = ":stamped_plugin_xml",
    deps = [
        ":plugin_library",
    ],
)

repackaged_files(
    name = "plugin_jar",
    srcs = [":otel_bazel"],
    prefix = "otel/lib",
)

plugin_deploy_zip(
    name = "otel_bazel_zip",
    srcs = [
        ":plugin_jar",
    ],
    zip_filename = "otel_bazel.zip",
)
