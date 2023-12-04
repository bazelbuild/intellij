# Testing infrastructure for the plugin's Skylark aspect

load(
    "//:build-visibility.bzl",
    "ASPECT_TEST_RULES_VISIBILITY_TO_ALL",
    "ASPECT_TEST_RULES_VISIBILITY_TO_TESTS",
)

licenses(["notice"])

proto_library(
    name = "intellij_aspect_test_fixture_proto",
    srcs = ["intellij_aspect_test_fixture.proto"],
    deps = ["//proto:intellij_ide_info_proto"],
)

java_proto_library(
    name = "intellij_aspect_test_fixture_java_proto",
    visibility = ASPECT_TEST_RULES_VISIBILITY_TO_TESTS,
    deps = [":intellij_aspect_test_fixture_proto"],
)

java_binary(
    name = "IntellijAspectTestFixtureBuilder",
    testonly = True,
    srcs = ["src/com/google/idea/blaze/aspect/IntellijAspectTestFixtureBuilder.java"],
    visibility = ASPECT_TEST_RULES_VISIBILITY_TO_TESTS,
    deps = [
        ":intellij_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//proto:intellij_ide_info_java_proto",
        "@com_google_protobuf//:protobuf_java",
    ],
)

java_library(
    name = "IntellijAspectTest",
    testonly = 1,
    srcs = ["src/com/google/idea/blaze/aspect/IntellijAspectTest.java"],
    visibility = ASPECT_TEST_RULES_VISIBILITY_TO_ALL,
    deps = [
        ":intellij_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//intellij_platform_sdk:jsr305",
        "//proto:common_java_proto",
        "//proto:intellij_ide_info_java_proto",
        "@junit_junit//jar",
    ],
)

proto_library(
    name = "fast_build_aspect_test_fixture_proto",
    testonly = True,
    srcs = ["fast_build_aspect_test_fixture.proto"],
    deps = ["//proto:fast_build_info_proto"],
)

java_proto_library(
    name = "fast_build_aspect_test_fixture_java_proto",
    testonly = True,
    visibility = ASPECT_TEST_RULES_VISIBILITY_TO_ALL,
    deps = [":fast_build_aspect_test_fixture_proto"],
)

java_binary(
    name = "FastBuildAspectTestFixtureBuilder",
    testonly = True,
    srcs = ["src/com/google/idea/blaze/aspect/FastBuildAspectTestFixtureBuilder.java"],
    visibility = ASPECT_TEST_RULES_VISIBILITY_TO_ALL,
    deps = [
        ":fast_build_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//proto:fast_build_info_java_proto",
        "@com_google_protobuf//:protobuf_java",
    ],
)

java_library(
    name = "FastBuildAspectLoader",
    testonly = True,
    srcs = ["src/com/google/idea/blaze/aspect/FastBuildAspectRule.java"],
    visibility = ASPECT_TEST_RULES_VISIBILITY_TO_ALL,
    deps = [
        ":fast_build_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "@junit_junit//jar",
    ],
)
