load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "shared",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//shared/java/com/google/idea/blaze/common",
        "//shared/java/com/google/idea/blaze/exception",
    ],
)

java_library(
    name = "exception",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//shared/java/com/google/idea/blaze/exception",
    ],
)

test_suite(
    name = "tests",
    tests = [
        "//shared/javatests/com/google/idea/blaze/common:LabelTest",
    ],
)
