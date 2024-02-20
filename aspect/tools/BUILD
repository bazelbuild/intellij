#
# Description:
# Tools needed by the bazel plugin's aspect.
#

package(default_visibility = ["//aspect:__pkg__"])

licenses(["notice"])

java_library(
    name = "lib",
    srcs = glob(["src/**/*.java"]),
    javacopts = ["-source 8 -target 8"],
    deps = [
        "@com_google_guava_guava//jar",
        "//proto:proto_deps",
        "@jsr305_annotations//jar",
        "//third_party/bazel/src/main/protobuf:worker_protocol_java_proto",
    ],
)

java_binary(
    name = "CreateAar",
    main_class = "com.google.idea.blaze.aspect.CreateAar",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_binary(
    name = "JarFilter",
    main_class = "com.google.idea.blaze.aspect.JarFilter",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_binary(
    name = "PackageParser",
    jvm_flags = [
        # quiet warnings from com.google.protobuf.UnsafeUtil,
        # see: https://github.com/google/protobuf/issues/3781
        "-XX:+IgnoreUnrecognizedVMOptions",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    ],
    main_class = "com.google.idea.blaze.aspect.PackageParser",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_library(
    name = "test_lib",
    testonly = 1,
    exports = [
        ":guava",
        ":lib",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:truth",
        "//proto:proto_deps",
        "@junit//jar",
    ],
)

java_test(
    name = "JarFilterTest",
    size = "medium",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/JarFilterTest.java"],
    test_class = "com.google.idea.blaze.aspect.JarFilterTest",
    deps = [":test_lib"],
)

java_test(
    name = "PackageParserTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/PackageParserTest.java"],
    test_class = "com.google.idea.blaze.aspect.PackageParserTest",
    deps = [
        ":test_lib",
        "@error_prone_annotations//jar",
    ],
)

java_test(
    name = "OptionParserTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/OptionParserTest.java"],
    test_class = "com.google.idea.blaze.aspect.OptionParserTest",
    deps = [":test_lib"],
)

java_test(
    name = "ArtifactLocationParserTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/ArtifactLocationParserTest.java"],
    test_class = "com.google.idea.blaze.aspect.ArtifactLocationParserTest",
    deps = [":test_lib"],
)

java_test(
    name = "CreateAarTestTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/CreateAarTest.java"],
    test_class = "com.google.idea.blaze.aspect.CreateAarTest",
    deps = [":test_lib"],
)
