# Integration tests for the plugin's Skylark aspect

load("@rules_java//java:defs.bzl", "java_library")
load(":defs.bzl", "repacked_files")

licenses(["notice"])

# To prevent versioning conflicts when developing internally, we always use the same
# guava version bundled with the IntelliJ plugin API.
java_library(
    name = "guava",
    testonly = True,
    visibility = ["//aspect/testing:__subpackages__"],
    exports = ["//intellij_platform_sdk:guava"],
)

java_library(
    name = "BazelIntellijAspectTest",
    testonly = 1,
    srcs = ["tests/src/com/google/idea/blaze/BazelIntellijAspectTest.java"],
    visibility = ["//aspect/testing:__subpackages__"],
    deps = [
        ":guava",
        "//aspect/testing/rules:IntellijAspectTest",
    ],
)

repacked_files(
    name = "bundled_aspect_files",
    testonly = 1,
    srcs = ["//aspect:aspect_files"],
    prefix = "bundled_aspect_files",
)

repacked_files(
    name = "bundled_aspect_template_files",
    testonly = 1,
    srcs = ["//aspect:aspect_template_files"],
    prefix = "bundled_aspect_template_files",
)

filegroup(
    name = "bundled_aspects",
    testonly = 1,
    data = [
        ":bundled_aspect_files",
        ":bundled_aspect_template_files",
    ],
    visibility = ["//visibility:public"],
)

repacked_files(
    name = "combined_aspects",
    testonly = 1,
    srcs = [
        "//aspect:aspect_files",
        "//aspect:aspect_template_files",
    ],
    visibility = ["//visibility:public"],
)
