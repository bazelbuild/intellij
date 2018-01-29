licenses(["notice"])  # Apache 2.0

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-kotlin.xml"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "kotlin",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//java",
    ],
)
