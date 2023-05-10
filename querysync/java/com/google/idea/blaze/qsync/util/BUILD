java_binary(
    name = "project_spec_builder",
    srcs = ["ProjectSpecBuilder.java"],
    main_class = "com.google.idea.blaze.qsync.util.ProjectSpecBuilder",
    deps = [
        ":cli_context",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "@com_google_protobuf//:protobuf_java",
    ],
)

java_binary(
    name = "query_summarizer",
    srcs = ["QuerySummarizer.java"],
    main_class = "com.google.idea.blaze.qsync.util.QuerySummarizer",
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/project:snapshot_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
    ],
)

java_library(
    name = "cli_context",
    srcs = ["CliContext.java"],
    deps = [
        "//shared",
    ],
)
