java_library(
    name = "QuerySummaryTestUtil",
    srcs = [
        "QuerySummaryTestBuilder.java",
        "QuerySummaryTestUtil.java",
    ],
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync:__subpackages__"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
        "//shared",
        "@com_google_guava_guava//jar",
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
        "//shared",
        "//third_party/bazel/src/main/protobuf:build_java_proto",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "QuerySpecTest",
    size = "small",
    srcs = ["QuerySpecTest.java"],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "@junit//jar",
        "@truth//jar",
    ],
)
