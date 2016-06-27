package(default_visibility = ["//visibility:public"])

java_library(
    name = "blaze-cpp",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//blaze-base",
        "//intellij-platform-sdk:plugin_api",
        "//third_party:jsr305",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-cpp.xml"],
)

java_library(
    name = "test_lib",
    srcs = glob(["tests/**/*.java"]),
    deps = [
        ":blaze-cpp",
        "//blaze-base:unit_test_utils",
        "//intellij-platform-sdk:plugin_api_for_tests",
        "//third_party:jsr305",
        "//third_party:test_lib",
    ],
)
