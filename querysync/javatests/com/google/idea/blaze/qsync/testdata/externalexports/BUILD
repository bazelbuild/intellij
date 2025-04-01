load("@rules_java//java:defs.bzl", "java_library")
load(":collect_deps.bzl", "java_info_txt")

java_library(
    name = "externalexports",
    srcs = ["TestClassUsingExternalExport.java"],
    deps = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata/exports"],
)

java_info_txt(
    name = "java_info",
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = [":externalexports"],
)
