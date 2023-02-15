proto_library(
    name = "project",
    srcs = ["project.proto"],
)

java_proto_library(
    name = "project_java_proto",
    visibility = ["//querysync:__subpackages__"],
    deps = [":project"],
)
