load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
)
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "gazelle",
    srcs = glob(["src/**/*.java"]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//common/settings",
        "//intellij_platform_sdk:plugin_api",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [],
    plugin_xmls = ["src/META-INF/blaze-gazelle.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":gazelle"],
)

stamped_plugin_xml(
    name = "gazelle_plugin_xml",
    plugin_id = "com.google.idea.blaze.gazelle",
    plugin_name = "com.google.idea.blaze.gazelle",
)

intellij_plugin(
    name = "gazelle_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":gazelle_plugin_xml",
    deps = [
        ":plugin_library",
        "//base:plugin_library",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    jvm_flags = [
        # These flags are necessary for jdk17, since it removes `--illegal-access`.
        "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.font=ALL-UNNAMED",
        "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
        "--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED",
        "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
    ],
    required_plugins = "com.google.idea.blaze.gazelle",
    tags = ["large"],
    test_package_root = "com.google.idea.blaze.gazelle",
    runtime_deps = [
        ":gazelle_integration_test_plugin",
    ],
    deps = [
        ":gazelle",
        "//base",
        "//base:integration_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
    ],
)
