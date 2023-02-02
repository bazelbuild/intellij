java_library(
    name = "QuerySummaryTestUtil",
    srcs = ["QuerySummaryTestUtil.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
    ],
)

java_test(
    name = "QuerySummaryTest",
    size = "small",
    srcs = ["QuerySummaryTest.java"],
    deps = [
        ":QuerySummaryTestUtil",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)
