#
# Description: A Skylark debugging client for IntelliJ.
#

licenses(["notice"])  # Apache 2.0

java_library(
    name = "skylark",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//common/actionhelper",
        "//common/binaryhelper",
        "//common/concurrency",
        "//common/experiments",
        "//common/formatter",
        "//common/guava",
        "//common/transactions",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//sdkcompat",
        "//third_party/auto_value",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-skylark.xml"],
    visibility = ["//visibility:public"],
)
