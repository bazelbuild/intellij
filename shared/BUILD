load(
    "//:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)

licenses(["notice"])

java_library(
    name = "shared",
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    exports = ["//shared/java/com/google/idea/blaze/common"],
)
