load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "nested",
    srcs = ["TestClassExternalDep.java"],
    visibility = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata:__subpackages__"],
    deps = ["@com_google_guava_guava//jar"],
)
