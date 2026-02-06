load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

java_library(
    name = "test_framework_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2026.1": [
            "@test_framework_261//jar",
            "@test_framework_common_261//jar",
            "@test_framework_core_261//jar",
            "@java_test_framework_261//jar",
            "@java_test_framework_shared_261//jar",
        ],
        "intellij-ue-2026.1": [
            "@test_framework_261//jar",
            "@test_framework_common_261//jar",
            "@test_framework_core_261//jar",
            "@java_test_framework_261//jar",
            "@java_test_framework_shared_261//jar",
        ],
        "default": [],
    }),
)

java_library(
    name = "util_internal",
    visibility = ["//visibility:private"],
    exports = select_for_plugin_api({
        "intellij-2026.1": [
            "@util_261//jar",
        ],
        "intellij-ue-2026.1": [
            "@util_261//jar",
        ],
        "default": [],
    }),
)

java_library(
    name = "test_framework",
    visibility = ["//visibility:public"],
    neverlink = 1,
    exports = [
        ":test_framework_internal",
    ],
)

# Runtime version for tests (without neverlink)
java_library(
    name = "test_framework_for_test",
    visibility = ["//visibility:public"],
    testonly = 1,
    exports = [
        ":test_framework_internal",
    ],
)