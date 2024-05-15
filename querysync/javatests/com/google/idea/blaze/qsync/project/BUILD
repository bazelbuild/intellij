load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "BuildGraphDataTest",
    srcs = ["BuildGraphDataTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata:build_graphs",
        "//shared",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "SnapshotSerializationTest",
    srcs = ["SnapshotSerializationTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//shared:vcs",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "ProjectDefinitionTest",
    size = "small",
    srcs = ["ProjectDefinitionTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "QuerySyncLanguageTest",
    size = "small",
    srcs = ["QuerySyncLanguageTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "TestSourceGlobMatcherTest",
    size = "small",
    srcs = ["TestSourceGlobMatcherTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)
