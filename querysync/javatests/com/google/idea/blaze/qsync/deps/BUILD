load("@rules_java//java:defs.bzl", "java_test")
load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "NewArtifactTrackerTest",
    size = "small",
    srcs = ["NewArtifactTrackerTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/deps:artifact_tracker_state_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/java",
        "//querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync/artifacts:aspect_protos",
        "//shared",
        "//shared:artifact",
        "//shared/javatests/com/google/idea/blaze/common:test_utils",
        "//shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
        "@mockito//jar",
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
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//shared",
        "//shared:vcs",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_test(
    name = "ArtifactDirectoryBuilderTest",
    size = "small",
    srcs = ["ArtifactDirectoryBuilderTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "@junit//jar",
        "@truth//jar",
        "@protobuf//:protobuf_java",
        "@com_google_guava_guava//jar",
    ],
)
