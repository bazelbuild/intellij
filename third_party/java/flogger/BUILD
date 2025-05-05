load("@rules_java//java:java_library.bzl", "java_library")

java_library(
    name = "flogger",
    visibility = ["//visibility:public"],
    exports = [
        "@flogger//jar",
    ],
    runtime_deps = [
        "@flogger_system_backend//jar",
    ],
)
