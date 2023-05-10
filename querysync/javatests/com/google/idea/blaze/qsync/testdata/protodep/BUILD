proto_library(
    name = "proto",
    srcs = ["testproto.proto"],
)

java_lite_proto_library(
    name = "proto_java_proto_lite",
    deps = [":proto"],
)

java_library(
    name = "protodep",
    srcs = ["TestClassProtoDep.java"],
    deps = [":proto_java_proto_lite"],
)
