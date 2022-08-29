# Integration tests for the plugin's Skylark aspect

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
