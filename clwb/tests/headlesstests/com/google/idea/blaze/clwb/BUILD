load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("//testing/bazel:bazel_test.bzl", "bazel_test")

kt_jvm_library(
    name = "base",
    testonly = 1,
    srcs = glob(["base/**"]),
    deps = [
        "//base",
        "//clwb:clwb_lib",
        "//cpp",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//shared",
        "//testing/src/com/google/idea/testing/headless",
        "//third_party/java/junit",
    ],
)

bazel_test(
    bazel_version = ">=8",
    os = ["windows"],
    project = "//clwb/tests/projects:clang_cl",
    test = "ClangClTest.java",
    deps = [":base"],
)

bazel_test(
    project = "//examples/cpp:simple_project_archive",
    test = "ExampleTest.java",
    deps = [":base"],
)

bazel_test(
    project = "//clwb/tests/projects:execution",
    test = "ExecutionTest.kt",
    deps = [":base"],
)

bazel_test(
    project = "//clwb/tests/projects:external_includes",
    test = "ExternalIncludesTest.java",
    deps = [":base"],
)

bazel_test(
    os = [
        "linux",
        "macos",
    ],
    project = "//clwb/tests/projects:simple",
    test = "LibCppTest.java",
    deps = [":base"],
)

bazel_test(
    bazel_version = ">=8",
    cache_flags = ["--platforms=@toolchains_llvm//platforms:wasm32"],
    os = [
        "linux",
        "macos",
    ],
    project = "//clwb/tests/projects:llvm_toolchain",
    test = "LlvmToolchainTest.java",
    deps = [":base"],
)

bazel_test(
    bazel_version = ">=8",
    project = "//clwb/tests/projects:protobuf",
    test = "ProtobufCacheTest.java",
    deps = [":base"],
)

bazel_test(
    bazel_version = ">=8",
    project = "//clwb/tests/projects:protobuf",
    test = "ProtobufTest.java",
    deps = [":base"],
)

bazel_test(
    project = "//clwb/tests/projects:simple",
    test = "SimpleTest.java",
    deps = [":base"],
)

bazel_test(
    project = "//clwb/tests/projects:target_compatible",
    test = "TargetCompatibleTest.java",
    deps = [":base"],
)

bazel_test(
    project = "//clwb/tests/projects:transition",
    test = "TransitionTest.java",
    deps = [":base"],
)

bazel_test(
    project = "//clwb/tests/projects:virtual_includes",
    test = "VirtualIncludesCacheTest.java",
    deps = [":base"],
)

bazel_test(
    project = "//clwb/tests/projects:virtual_includes",
    test = "VirtualIncludesTest.java",
    deps = [":base"],
)
