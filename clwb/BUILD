#
# Description: Builds clwb
#

load("@rules_java//java:defs.bzl", "java_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load("//:version.bzl", "VERSION")
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//build_defs:intellij_plugin_debug.bzl",
    "intellij_plugin_debug_target",
)
load("//build_defs:intellij_plugin_library.bzl", "intellij_plugin_library")
load("//build_defs:intellij_plugin_zip.bzl", "intellij_plugin_zip")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_version")
load(
    "//testing:test_defs.bzl",
    "intellij_unit_test_suite",
)
load(":test_defs.bzl", "clwb_headless_test", "clwb_integration_test")

licenses(["notice"])

intellij_plugin_library(
    name = "plugin_library",
    data = {"gdb/gdbserver": ":gdbserver"},
    optional_plugin_xmls = ["optional_clwb_oclang"],
    plugin_xmls = ["src/META-INF/clwb.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":clwb_lib",
        "//clwb/src/com/google/idea/blaze/clwb/radler",
    ],
)

optional_plugin_xml(
    name = "optional_clwb_oclang",
    module = ["com.intellij.cidr.lang"],
    plugin_xml = "src/META-INF/clwb-oclang.xml",
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    description_file = "src/META-INF/description.html",
    plugin_id = "com.google.idea.bazel.clwb",
    plugin_name = "Bazel for CLion",
    # #api212: We depend on an API which is only contained in 2021.2.1+.
    since_build_numbers = {"212": "212.5080.55"},
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

kt_jvm_library(
    name = "clwb_lib",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//clwb:__subpackages__"],
    deps = [
        "//base",
        "//base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//common/actions",
        "//common/experiments",
        "//common/util:datafiles",
        "//cpp",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//sdkcompat",
        "//shared",
    ],
)

intellij_plugin(
    name = "clwb_bazel",
    plugin_icons = ["//common:pluginIcon.svg"],
    plugin_xml = ":stamped_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//base:plugin_library",
        "//cpp:plugin_library",
        "//dart:plugin_library",
        "//javascript:plugin_library",
        "//python:plugin_library",
        "//skylark:plugin_library",
        "//terminal:plugin_library",
    ],
)

intellij_plugin_debug_target(
    name = "clwb_bazel_dev",
    prefix = "clwb",
    deps = [":clwb_bazel"],
)

intellij_plugin_zip(
    name = "clwb_bazel_zip",
    filename = "clwb_bazel.zip",
    prefix = "clwb",
    deps = [":clwb_bazel"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    test_package_root = "com.google.idea.blaze.clwb",
    deps = [
        ":clwb_lib",
        "//base",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//third_party/java/junit",
    ],
)

clwb_headless_test(
    name = "simple_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/SimpleTest.java"],
    project = "simple",
)

clwb_headless_test(
    name = "virtual_includes_headless_test",
    srcs = [
        "tests/headlesstests/com/google/idea/blaze/clwb/VirtualIncludesCacheTest.java",
        "tests/headlesstests/com/google/idea/blaze/clwb/VirtualIncludesTest.java",
    ],
    project = "virtual_includes",
)

clwb_headless_test(
    name = "llvm_toolchain_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/LlvmToolchainTest.java"],
    project = "llvm_toolchain",
)

clwb_headless_test(
    name = "external_includes_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/ExternalIncludesTest.java"],
    project = "external_includes",
)

clwb_headless_test(
    name = "execution_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/ExecutionTest.java"],
    project = "execution",
)

clwb_headless_test(
    name = "target_compatible_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/TargetCompatibleTest.java"],
    project = "target_compatible",
)

clwb_headless_test(
    name = "example_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/ExampleTest.java"],
    example = "simple_project",
)

clwb_headless_test(
    name = "lib_cpp_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/LibCppTest.java"],
    project = "simple",
)

clwb_headless_test(
    name = "clang_cl_headless_test",
    srcs = ["tests/headlesstests/com/google/idea/blaze/clwb/ClangClTest.java"],
    project = "clang_cl",
)

clwb_headless_test(
    name = "protobuf_headless_test",
    srcs = [
        "tests/headlesstests/com/google/idea/blaze/clwb/ProtobufCacheTest.java",
        "tests/headlesstests/com/google/idea/blaze/clwb/ProtobufTest.java",
    ],
    project = "protobuf",
)

test_suite(
    name = "headless_tests",
    tests = [
        ":clang_cl_headless_test",
        ":example_headless_test",
        ":execution_headless_test",
        ":external_includes_headless_test",
        ":lib_cpp_headless_test",
        ":llvm_toolchain_headless_test",
        ":protobuf_headless_test",
        ":simple_headless_test",
        ":target_compatible_headless_test",
        ":virtual_includes_headless_test",
    ],
)

clwb_integration_test(
    name = "copts_processor",
    srcs = ["tests/integrationtests/com/google/idea/blaze/clwb/CoptsProcessorTest.kt"],
)

test_suite(
    name = "integration_tests",
    tests = [
        ":copts_processor",
    ],
)
