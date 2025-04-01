load(":collect_deps.bzl", "java_info_txt")

java_library(
    name = "workspacerootincluded",
    srcs = ["MyClass.java"],
)

java_info_txt(
    name = "java_info",
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = [":workspacerootincluded"],
)
