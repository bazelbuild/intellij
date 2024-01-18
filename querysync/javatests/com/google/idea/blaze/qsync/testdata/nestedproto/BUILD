proto_library(
    name = "nested_proto",
    srcs = ["java/com/testdata/nestedproto/proto/nested.proto"],
)

java_lite_proto_library(
    name = "nested_java_proto_lite",
    deps = [":nested_proto"],
)

java_library(
    name = "consumer",
    srcs = ["java/com/testdata/nestedproto/NestedProtoConsumer.java"],
    deps = [":nested_java_proto_lite"],
)
