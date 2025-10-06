# Integration tests for the plugin's Starlark aspect

load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])

# To prevent versioning conflicts when developing internally, we always use the same
# guava version bundled with the IntelliJ plugin API.
java_library(
    name = "guava",
    testonly = True,
    visibility = ["//aspect/testing:__subpackages__"],
    exports = ["//intellij_platform_sdk:guava"],
)

java_library(
    name = "BazelIntellijAspectTest",
    testonly = 1,
    srcs = ["tests/src/com/google/idea/blaze/BazelIntellijAspectTest.java"],
    visibility = ["//aspect/testing:__subpackages__"],
    deps = [
        ":guava",
        "//aspect/testing/rules:IntellijAspectTest",
    ],
)

# All aspect tests.
test_suite(
    name = "aspect_tests",
    tests = [
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/ccbinary:CcBinaryTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/cclibrary:CcLibraryTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/cctest:CcTestTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/cctoolchain:CcToolchainTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/coptsmakevars:CoptsMakeVarsTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/transition:TransitionTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/general/alias:AliasTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/general/analysistest:AnalysisTestTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/general/artifacts:ArtifactTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/general/build:BuildFileTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/general/noide:NoIdeTest",
        "//aspect/testing/tests/src/com/google/idea/blaze/aspect/general/tags:TagTest",
    ],
)
