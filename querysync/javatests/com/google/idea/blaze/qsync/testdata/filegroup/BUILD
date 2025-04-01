load("@rules_java//java:defs.bzl", "java_library")

filegroup(
    name = "nested_filegroup",
    srcs = ["TestSubFileGroupSource.java"],
)

filegroup(
    name = "sources",
    srcs = [
        "TestFileGroupSource.java",
        ":nested_filegroup",
    ],
)

java_library(
    name = "filegroup",
    srcs = [":sources"],
    deps = ["@com_google_guava_guava//jar"],
)
