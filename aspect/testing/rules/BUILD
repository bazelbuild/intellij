# Testing infrastructure for the plugin's Skylark aspect

licenses(["notice"])  # Apache 2.0

proto_library(
    name = "intellij_aspect_test_fixture_proto",
    srcs = ["intellij_aspect_test_fixture.proto"],
    deps = ["//proto:intellij_ide_info_proto"],
)

java_proto_library(
    name = "intellij_aspect_test_fixture_java_proto",
    visibility = [
        "//aspect/testing/tests:__subpackages__",
    ],
    deps = [":intellij_aspect_test_fixture_proto"],
)

java_binary(
    name = "IntellijAspectTestFixtureBuilder",
    srcs = ["src/com/google/idea/blaze/aspect/IntellijAspectTestFixtureBuilder.java"],
    visibility = [
        "//aspect/testing/tests:__subpackages__",
    ],
    deps = [
        ":intellij_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//proto:intellij_ide_info_java_proto",
        "@com_google_protobuf_java//:protobuf_java",
    ],
)

java_library(
    name = "IntellijAspectTest",
    testonly = 1,
    srcs = ["src/com/google/idea/blaze/aspect/IntellijAspectTest.java"],
    visibility = [
        "//aspect/testing:__subpackages__",
    ],
    deps = [
        ":intellij_aspect_test_fixture_java_proto",
        "//aspect/testing:guava",
        "//proto:intellij_ide_info_java_proto",
        "@jsr305_annotations//jar",
        "@junit//jar",
    ],
)
