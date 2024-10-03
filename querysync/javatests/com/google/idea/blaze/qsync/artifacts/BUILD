load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "ArtifactDirectoryUpdateTest",
    srcs = ["ArtifactDirectoryUpdateTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "//shared:artifact",
        "@com_google_guava_guava//jar",
        "@jsr305_annotations//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)
