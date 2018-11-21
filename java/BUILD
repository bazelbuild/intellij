licenses(["notice"])  # Apache 2.0

load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "merged_plugin_xml",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

java_library(
    name = "java",
    srcs = glob(["src/**/*.java"]),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//common/actions"],
    deps = [
        ":neverlinked_jpda-jdi",
        "//base",
        "//common/actions",
        "//common/concurrency",
        "//common/experiments",
        "//common/transactions",
        "//intellij_platform_sdk:coverage",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:junit",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//sdkcompat",
        "//third_party/auto_value",
    ],
)

# This is provided by the IDE at runtime
java_library(
    name = "neverlinked_jpda-jdi",
    neverlink = 1,
    exports = ["//third_party:jpda-jdi"],
)

filegroup(
    name = "plugin_xml",
    srcs = ["src/META-INF/blaze-java.xml"],
    visibility = ["//visibility:public"],
)

merged_plugin_xml(
    name = "merged_plugin_xml",
    srcs = [
        "//base:plugin_xml",
    ] + [
        ":plugin_xml",
    ],
)

stamped_plugin_xml(
    name = "java_plugin_xml",
    plugin_id = "com.google.idea.blaze.java",
    plugin_name = "com.google.idea.blaze.java",
    plugin_xml = "merged_plugin_xml",
)

optional_plugin_xml(
    name = "optional_java",
    module = "JUnit",
    plugin_xml = "src/META-INF/java-contents.xml",
    visibility = ["//visibility:public"],
)

optional_plugin_xml(
    name = "optional_coverage",
    module = "com.intellij.modules.coverage",
    plugin_xml = "src/META-INF/coverage-contents.xml",
    visibility = ["//visibility:public"],
)

intellij_plugin(
    name = "java_integration_test_plugin",
    testonly = 1,
    optional_plugin_xmls = [
        ":optional_java",
        ":optional_coverage",
    ],
    plugin_xml = ":java_plugin_xml",
    deps = [
        ":java",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    # Used for the compiler tests
    data = [
        ":guava_lib_deploy.jar",
        ":javac_lib_deploy.jar",
        ":truth_lib_deploy.jar",
    ],
    jvm_flags = [
        "-Dguava.jar=$(location :guava_lib_deploy.jar)",
        "-Djavac.jar=$(location :javac_lib_deploy.jar)",
        "-Dtruth.jar=$(location :truth_lib_deploy.jar)",
    ],
    test_package_root = "com.google.idea.blaze.java",
    deps = [
        ":java",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:coverage_for_tests",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:truth",
        "//proto:proto_deps",
        "@junit//jar",
    ],
)

java_binary(
    name = "guava_lib",
    main_class = "None",
    runtime_deps = ["//intellij_platform_sdk:guava"],
)

java_binary(
    name = "javac_lib",
    main_class = "None",
    runtime_deps = [":jdk_tools_jar"],
)

java_import(
    name = "jdk_tools_jar",
    jars = ["@local_jdk//:langtools"],
)

java_binary(
    name = "truth_lib",
    testonly = 1,
    main_class = "None",
    runtime_deps = ["//intellij_platform_sdk:truth"],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    required_plugins = "com.google.idea.blaze.java",
    test_package_root = "com.google.idea.blaze.java",
    runtime_deps = [
        ":java_integration_test_plugin",
    ],
    deps = [
        ":java",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//proto:proto_deps",
        "@junit//jar",
    ],
)
