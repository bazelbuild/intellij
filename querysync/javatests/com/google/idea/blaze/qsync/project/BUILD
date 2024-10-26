load("@rules_java//java:defs.bzl", "java_test")
load("//:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

java_test(
    name = "BuildGraphDataTest",
    srcs = ["BuildGraphDataTest.java"],
    tags = [
        "no_tap",
    ],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata:build_graphs",
        "//shared",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
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
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_test(
    name = "ProjectDefinitionTest",
    size = "small",
    srcs = ["ProjectDefinitionTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_test(
    name = "QuerySyncLanguageTest",
    size = "small",
    srcs = ["QuerySyncLanguageTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "@junit//jar",
        "@protobuf//:protobuf_java",
        "@truth//jar",
        "@protobuf//:protobuf_java",
    ],
)

java_test(
    name = "TestSourceGlobMatcherTest",
    size = "small",
    srcs = ["TestSourceGlobMatcherTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_test(
    name = "DepsGraphTest",
    size = "small",
    srcs = ["DepsGraphTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_test(
    name = "ExternalTransitiveClosureTest",
    size = "small",
    srcs = ["ExternalTransitiveClosureTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_test(
    name = "PendingExternalDepsTest",
    size = "small",
    srcs = ["PendingExternalDepsTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@junit//jar",
        "@truth//jar",
        "@com_google_guava_guava//jar",
    ],
)
