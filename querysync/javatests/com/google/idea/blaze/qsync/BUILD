java_library(
    name = "TestUtils",
    testonly = 1,
    srcs = [
        "LoggingContext.java",
        "NoopContext.java",
        "QuerySyncTestUtils.java",
        "TestDataSyncRunner.java",
    ],
    visibility = [
        ":__subpackages__",
        "//base:__subpackages__",
    ],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//shared",
        "//shared:vcs",
        "@com_google_guava_guava//jar",
        "@flogger//jar",
        "@flogger_system_backend//jar",
    ],
)

java_test(
    name = "QuerySyncTestUtilsTest",
    srcs = ["QuerySyncTestUtilsTest.java"],
    deps = [
        ":TestUtils",
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
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
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
        "//querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//shared:vcs",
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
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync/query:QuerySummaryTestUtil",
        "//shared:vcs",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "PartialProjectRefreshTest",
    size = "small",
    srcs = ["PartialProjectRefreshTest.java"],
    deps = [
        ":TestUtils",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//shared",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "GeneratedSourceProjectUpdaterTest",
    size = "small",
    srcs = ["GeneratedSourceProjectUpdaterTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata:project_protos",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "SrcJarProjectUpdaterTest",
    size = "small",
    srcs = ["SrcJarProjectUpdaterTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata:project_protos",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)
