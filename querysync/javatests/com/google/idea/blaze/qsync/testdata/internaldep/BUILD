load("@rules_java//java:defs.bzl", "java_library")
load(":collect_deps.bzl", "java_info_txt")

java_library(
    name = "internaldep",
    srcs = ["TestClassInternalDep.java"],
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"],
)

java_info_txt(
    name = "java_info",
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = [":internaldep"],
)