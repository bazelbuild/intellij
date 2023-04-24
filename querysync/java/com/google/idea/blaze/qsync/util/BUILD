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

java_library(
    name = "cli_context",
    srcs = ["CliContext.java"],
    deps = [
        "//shared",
    ],
)
