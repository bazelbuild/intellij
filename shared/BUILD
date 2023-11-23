load(
    "//:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "shared",
    visibility = PLUGIN_PACKAGES_VISIBILITY + COMMON_PLUGINS_VISIBILITY,
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

java_library(
    name = "vcs",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = [
        "//shared/java/com/google/idea/blaze/common/vcs",
    ],
)

test_suite(
    name = "tests",
    tests = [
        "//shared/javatests/com/google/idea/blaze/common:LabelTest",
        "//shared/javatests/com/google/idea/blaze/common/vcs:VcsStateTest",
    ],
)
