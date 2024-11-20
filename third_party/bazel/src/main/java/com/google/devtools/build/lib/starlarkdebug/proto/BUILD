load("@com_google_protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@com_google_protobuf//bazel:proto_library.bzl", "proto_library")

package(default_visibility = ["//src:__subpackages__"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//third_party/bazel/src/main/java/com/google/devtools/build/lib:__pkg__"],
)

java_proto_library(
    name = "starlark_debugging_java_proto",
    # The Bazel IntelliJ plugin uses this.
    # Other clients must not assume protocol stability.
    visibility = ["//visibility:public"],
    deps = ["starlark_debugging_proto"],
)

proto_library(
    name = "starlark_debugging_proto",
    srcs = ["starlark_debugging.proto"],
    visibility = ["//visibility:public"],
)
