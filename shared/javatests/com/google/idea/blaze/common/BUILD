load("@rules_java//java:defs.bzl", "java_library", "java_test")

package(
    default_visibility = [
        "//base:__subpackages__",
        "//querysync:__subpackages__",
        "//shared:__subpackages__",
    ],
)

java_test(
    name = "LabelTest",
    size = "small",
    srcs = ["LabelTest.java"],
    deps = [
        "//shared/java/com/google/idea/blaze/common",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_library(
    name = "test_utils",
    srcs = [
        "LoggingContext.java",
        "NoopContext.java",
    ],
    deps = [
        "//shared/java/com/google/idea/blaze/common",
        "@flogger//jar",
        "@flogger_system_backend//jar",
    ],
)

java_test(
    name = "TargetTreeTest",
    size = "small",
    srcs = ["TargetTreeTest.java"],
    deps = [
        "//shared",
        "@junit//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "AtomicFileWriterTest",
    size = "small",
    srcs = ["AtomicFileWriterTest.java"],
    deps = [
        "//shared",
        "@junit//jar",
        "@truth//jar",
    ],
)
