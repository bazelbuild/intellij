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
load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])  # Apache 2.0

java_library(
    name = "cpp",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//sdkcompat",
        "//third_party/auto_value",
    ],
)

optional_plugin_xml(
    name = "optional_cidr",
    module = "com.intellij.modules.cidr.lang",
    plugin_xml = "src/META-INF/blaze-cpp.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_cidr"],
    visibility = ["//visibility:public"],
    deps = [":cpp"],
)

java_library(
    name = "clion_sysprop_rule",
    testonly = 1,
    srcs = ["tests/utils/com/google/idea/blaze/cpp/ClionUnitTestSystemPropertiesRule.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "@junit//jar",
    ],
)

# exposed so plugins can optionally depend on it
java_library(
    name = "compiler_version_checker",
    srcs = ["src/com/google/idea/blaze/cpp/CompilerVersionChecker.java"],
    neverlink = 1,
    deps = ["//intellij_platform_sdk:plugin_api"],
)

# exposed so plugins can optionally depend on it
java_library(
    name = "compiler_wrapper_provider",
    srcs = ["src/com/google/idea/blaze/cpp/CompilerWrapperProvider.java"],
    neverlink = 1,
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    class_rules = select_for_ide(
        clion = ["com.google.idea.blaze.cpp.ClionUnitTestSystemPropertiesRule"],
        default = [],
    ),
    test_package_root = "com.google.idea.blaze.cpp",
    # Needed to work around one-version issue
    runtime_deps = ["//intellij_platform_sdk:test_libs"],
    deps = [
        ":cpp",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//testing:lib",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
    ] + select_for_ide(
        clion = [":clion_sysprop_rule"],
        default = [],
    ),
)

stamped_plugin_xml(
    name = "cpp_plugin_xml",
    plugin_id = "com.google.idea.bazel.cpp",
    plugin_name = "com.google.idea.bazel.cpp",
)

intellij_plugin(
    name = "cpp_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":cpp_plugin_xml",
    deps = [
        ":plugin_library",
        "//base:plugin_library",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = [
        "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppIntegrationTestCase.java",
        "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppResolvingTestCase.java",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//sdkcompat",
        "//testing:lib",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(
        ["tests/integrationtests/**/*.java"],
        exclude = [
            "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppIntegrationTestCase.java",
            "tests/integrationtests/com/google/idea/blaze/cpp/BlazeCppResolvingTestCase.java",
        ],
    ),
    required_plugins = "com.google.idea.bazel.cpp",
    test_package_root = "com.google.idea.blaze.cpp",
    runtime_deps = [
        ":cpp_integration_test_plugin",
    ],
    deps = [
        ":cpp",
        ":integration_test_utils",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//sdkcompat",
        "@junit//jar",
    ],
)
