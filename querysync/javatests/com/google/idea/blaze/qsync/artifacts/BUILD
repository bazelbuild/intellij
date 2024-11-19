load("@rules_java//java:defs.bzl", "java_test")
load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "ArtifactDirectoryUpdateTest",
    srcs = ["ArtifactDirectoryUpdateTest.java"],
    deps = [
        ":mock_artifact_cache",
        "@jsr305_annotations//jar",
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "//shared:artifact",
        "//third_party/java/junit",
        "//third_party/java/truth",
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "mock_artifact_cache",
    srcs = ["MockArtifactCache.java"],
    deps = [
        "//shared",
        "//shared:artifact",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
    ],
)

java_library(
    name = "aspect_protos",
    srcs = ["AspectProtos.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts:build_artifact_java_proto",
        "@com_google_guava_guava//jar",
    ],
)
