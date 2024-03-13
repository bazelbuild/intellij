load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "NewArtifactTrackerTest",
    size = "small",
    srcs = ["NewArtifactTrackerTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/java",
        "//querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//shared",
        "//shared:artifact",
        "//shared:vcs",
        "//shared/javatests/com/google/idea/blaze/common:test_utils",
        "//shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@mockito//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "ArtifactTrackerStateSerializationTest",
    size = "small",
    srcs = ["ArtifactTrackerStateSerializationTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/deps:artifact_tracker_state_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/java",
        "//shared",
        "//shared:vcs",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "AddCompiledJavaDepsTest",
    size = "small",
    srcs = ["AddCompiledJavaDepsTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/java",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata:project_protos",
        "//shared",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)
