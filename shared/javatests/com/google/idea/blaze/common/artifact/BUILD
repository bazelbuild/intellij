load("@rules_java//java:defs.bzl", "java_library", "java_test")

package(
    default_visibility = [
        "//base:__subpackages__",
        "//querysync:__subpackages__",
        "//shared:__subpackages__",
    ],
)

java_test(
    name = "BuildArtifactCacheTest",
    size = "small",
    srcs = ["BuildArtifactCacheTest.java"],
    deps = [
        ":test_utils",
        "//shared/java/com/google/idea/blaze/common/artifact",
        "//shared/javatests/com/google/idea/blaze/common:test_utils",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_library(
    name = "test_utils",
    srcs = [
        "TestArtifactFetcher.java",
        "TestOutputArtifact.java",
    ],
    deps = [
        "//shared/java/com/google/idea/blaze/common",
        "//shared/java/com/google/idea/blaze/common/artifact",
        "//third_party/auto_value",
        "@com_google_guava_failureaccess//jar",
        "@com_google_guava_guava//jar",
        "@jsr305_annotations//jar",
    ],
)
