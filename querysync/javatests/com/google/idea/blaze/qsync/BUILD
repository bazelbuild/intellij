java_library(
    name = "TestUtils",
    srcs = ["QuerySyncTestUtils.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/vcs",
        "//shared",
        "@com_google_guava_guava//jar",
    ],
)

java_test(
    name = "BuildGraphTest",
    srcs = ["BuildGraphTest.java"],
    deps = [
        ":TestUtils",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "GraphToProjectConverterTest",
    srcs = ["GraphToProjectConverterTest.java"],
    deps = [
        ":TestUtils",
        "//querysync/java/com/google/idea/blaze/qsync",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "AffectedPackagesTest",
    size = "small",
    srcs = ["AffectedPackagesTest.java"],
    deps = [
        ":TestUtils",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/vcs",
        "//querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "ProjectRefresherTest",
    size = "small",
    srcs = ["ProjectRefresherTest.java"],
    deps = [
        ":TestUtils",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/vcs",
        "//querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "PartialProjectUpdateTest",
    size = "small",
    srcs = ["PartialProjectUpdateTest.java"],
    deps = [
        ":TestUtils",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)
