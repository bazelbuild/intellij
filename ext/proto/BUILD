load("//third_party/java/grpc:build_defs.bzl", "java_grpc_library")
load("//net/grpc/go/build_defs:go_grpc_library.bzl", "go_grpc_library")

proto_library(
    name = "intellij_ext_proto",
    srcs = ["intellij-ext.proto"],
    has_services = 1,
    use_java_stubby_library = True,
)

java_proto_library(
    name = "intellij_ext_java_proto",
    visibility = ["//visibility:public"],
    deps = [":intellij_ext_proto"],
)

java_grpc_library(
    name = "intellij_ext_java_grpc",
    srcs = [":intellij_ext_proto"],
    visibility = ["//visibility:public"],
    deps = [":intellij_ext_java_proto"],
)

go_proto_library(
    name = "intellij_ext_go_proto",
    visibility = ["//visibility:public"],
    deps = [":intellij_ext_proto"],
)

go_grpc_library(
    name = "intellij_ext_go_grpc",
    srcs = [":intellij_ext_proto"],
    visibility = ["//visibility:public"],
    deps = [":intellij_ext_go_proto"],
)
