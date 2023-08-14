load("//third_party/java/grpc:build_defs.bzl", "java_grpc_library")
load("//net/grpc/go/build_defs:go_grpc_library.bzl", "go_grpc_library")

proto_library(
    name = "intellijext_proto",
    srcs = ["intellijext.proto"],
    has_services = 1,
    tags = ["manual"],
    use_java_stubby_library = True,
)

java_proto_library(
    name = "intellijext_java_proto",
    tags = ["manual"],
    visibility = ["//visibility:public"],
    deps = [":intellijext_proto"],
)

java_grpc_library(
    name = "intellijext_java_grpc",
    srcs = [":intellijext_proto"],
    tags = ["manual"],
    visibility = ["//visibility:public"],
    deps = [":intellijext_java_proto"],
)

go_proto_library(
    name = "intellijext_go_proto",
    tags = ["manual"],
    visibility = ["//visibility:public"],
    deps = [":intellijext_proto"],
)

go_grpc_library(
    name = "intellijext_go_grpc",
    srcs = [":intellijext_proto"],
    tags = ["manual"],
    visibility = ["//visibility:public"],
    deps = [":intellijext_go_proto"],
)
