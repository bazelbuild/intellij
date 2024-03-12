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
