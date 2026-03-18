load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

# some targets have to be executed as part of intellij - where it has to use platform's stdlib - and as standalone
# targets - where it has to use maven's stdlib
java_library(
    name = "kotlin_stdlib",
    neverlink = 1,
    exports = ["@maven//:org_jetbrains_kotlin_kotlin_stdlib"],
)

kt_jvm_library(
    name = "lib",
    srcs = ["lib.kt"],
    deps = [":kotlin_stdlib"],
)

kt_jvm_library(
    name = "zip",
    srcs = ["zip.kt"],
    deps = [":kotlin_stdlib"],
)

proto_library(
    name = "cache_proto",
    srcs = ["cache.proto"],
)

java_proto_library(
    name = "cache_java_proto",
    deps = [":cache_proto"],
)

kt_jvm_library(
    name = "cache_lib",
    srcs = ["cache.kt"],
    deps = [
        ":cache_java_proto",
        ":kotlin_stdlib",
        ":lib",
        ":zip",
        "@protobuf//:protobuf_java",
    ],
)

java_binary(
    name = "cache_bin",
    main_class = "com.google.idea.testing.bazel.CacheKt",
    visibility = ["//visibility:public"],
    runtime_deps = [
        ":cache_lib",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
    ],
)

proto_library(
    name = "fixture_proto",
    srcs = ["fixture.proto"],
)

java_proto_library(
    name = "fixture_java_proto",
    deps = [":fixture_proto"],
)

kt_jvm_library(
    name = "fixture_lib",
    testonly = 1,
    srcs = ["fixture.kt"],
    visibility = ["//visibility:public"],
    deps = [
        ":fixture_java_proto",
        ":kotlin_stdlib",
        ":lib",
        ":zip",
        "//third_party/java/junit",
        "@protobuf//:protobuf_java",
        "@rules_java//java/runfiles",
    ],
)
