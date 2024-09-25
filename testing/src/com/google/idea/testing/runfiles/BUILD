load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

java_library(
    name = "runfiles",
    testonly = 1,
    srcs = glob(["*.java"]),
)
