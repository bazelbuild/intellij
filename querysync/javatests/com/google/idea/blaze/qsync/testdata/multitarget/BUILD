# A blaze package containing a source file that's compiled for two targets.

load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "nodeps",
    srcs = ["TestClassMultiTarget.java"],
)

java_library(
    name = "externaldep",
    srcs = [
        "TestClassMultiTarget.java",
        "TestClassSingleTarget.java",
    ],
    deps = ["@com_google_guava_guava//jar"],
)
