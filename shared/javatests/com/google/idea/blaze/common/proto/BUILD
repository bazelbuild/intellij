proto_library(
    name = "test_proto",
    srcs = ["test_message.proto"],
)

java_proto_library(
    name = "test_java_proto",
    deps = [":test_proto"],
)

java_test(
    name = "ProtoStringInternerTest",
    size = "small",
    srcs = ["ProtoStringInternerTest.java"],
    deps = [
        ":test_java_proto",
        "//shared/java/com/google/idea/blaze/common/proto",
        "@com_google_guava_guava//jar",
        "@com_google_protobuf//:protobuf_java",
        "@junit//jar",
        "@truth//jar",
    ],
)
