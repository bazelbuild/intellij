load("@rules_java//java:defs.bzl", "java_library", "java_test")

package(
    default_visibility = [
        "//base:__subpackages__",
        "//shared:__subpackages__",
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
        "//third_party/java/flogger",
    ],
)

