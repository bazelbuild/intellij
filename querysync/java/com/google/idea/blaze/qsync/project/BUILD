proto_library(
    name = "project",
    srcs = ["project.proto"],
    deps = [
        "//proto:intellij_ide_info_proto",
        "//third_party/bazel/src/main/java/com/google/devtools/build/lib/buildeventstream/proto:build_event_stream_proto",
    ],
)

java_proto_library(
    name = "project_java_proto",
    visibility = ["//querysync:__subpackages__"],
    deps = [":project"],
)
