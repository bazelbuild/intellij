load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
    "RUST_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "rust",
    srcs = glob(["src/**/*.java"]),
    visibility = RUST_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//common/experiments",
        "//common/util:transactions",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//sdkcompat",
#        "//third_party/rust",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [],
    plugin_xmls = ["src/META-INF/blaze-rust.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":rust"],
)

stamped_plugin_xml(
    name = "rust_plugin_xml",
    plugin_id = "com.google.idea.blaze.rust",
    plugin_name = "com.google.idea.blaze.rust",
)

intellij_plugin(
    name = "rust_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":rust_plugin_xml",
    deps = [
        ":plugin_library",
        "//base:plugin_library",
    ],
)

