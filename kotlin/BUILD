licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "merged_plugin_xml",
    "stamped_plugin_xml",
    "intellij_plugin",
    "optional_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
    "intellij_integration_test_suite",
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-kotlin.xml"],
    visibility = ["//visibility:public"],
)

#optional_plugin_xml(
#    name = "optional_xml",
#    module = "org.intellij.scala",
#    plugin_xml = "src/META-INF/kotlin-contents.xml",
#    visibility = ["//visibility:public"],
#)
#
#OPTIONAL_PLUGIN_XMLS = [
#    ":optional_xml",
#    "//java:optional_java",
#]
#
#merged_plugin_xml(
#    name = "merged_plugin_xml",
#    srcs = [
#        ":plugin_xml",
#        "//base:plugin_xml",
#        "//java:plugin_xml",
#    ],
#)
#
#stamped_plugin_xml(
#    name = "kotlin_plugin_xml",
#    plugin_id = "com.google.idea.blaze.kotlin",
#    plugin_name = "com.google.idea.blaze.kotlin",
#    plugin_xml = "merged_plugin_xml",
#)

java_library(
    name = "kotlin",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//third_party/kotlin",
        "@jsr305_annotations//jar",
    ],
)