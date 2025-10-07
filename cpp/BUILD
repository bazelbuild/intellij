load("@rules_java//java:defs.bzl", "java_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load(
    "//:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide", "select_for_version")
load("//testing:test_defs.bzl", "intellij_unit_test_suite")

kt_jvm_library(
    name = "cpp",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//base",
        "//common/experiments",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//querysync",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//sdkcompat",
        "//shared",
        "//third_party/java/auto_value",
        "@error_prone_annotations//jar",
    ],
)

stamped_plugin_xml(
    name = "non_optional_cidr",
    plugin_xml = "src/META-INF/blaze-cpp.xml",
)

optional_plugin_xml(
    name = "optional_cidr",
    module = ["com.intellij.cidr.lang"],
    plugin_xml = "src/META-INF/blaze-cpp-oclang.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_cidr"],
    plugin_xmls = [":non_optional_cidr"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":cpp"],
)

java_library(
    name = "clion_sysprop_rule",
    testonly = 1,
    srcs = ["tests/utils/com/google/idea/blaze/cpp/ClionUnitTestSystemPropertiesRule.java"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/java/junit",
    ],
)

java_library(
    name = "mock_xcode_settings_provider",
    testonly = 1,
    srcs = ["tests/utils/com/google/idea/blaze/cpp/MockXcodeSettingsProvider.java"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":cpp",
        "//base",
        "//intellij_platform_sdk:plugin_api",
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
        default = [],
    ),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.cpp",
    visibility = DEFAULT_TEST_VISIBILITY,
    # Needed to work around one-version issue
    runtime_deps = ["//intellij_platform_sdk:test_libs"],
    deps = [
        ":cpp",
        ":mock_xcode_settings_provider",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//third_party/java/junit",
        "@com_google_guava_guava//jar",
        "@maven//:org_mockito_mockito_core",
    ] + select_for_ide(
        default = [],
    ),
)
