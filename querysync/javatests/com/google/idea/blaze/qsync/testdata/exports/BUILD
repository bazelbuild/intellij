load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "exports",
    srcs = ["TestClassUsingExport.java"],
    deps = [":exported-collect"],
)

java_library(
    name = "exported-collect",
    exports = ["@com_google_guava_guava//jar"],
)
