load("@rules_java//java:defs.bzl", "java_test")

package(
    default_visibility = [":visibility"],
)

package_group(
    name = "visibility",
    packages = [
        "//shared",
    ],
)

java_test(
    name = "VcsStateTest",
    size = "small",
    srcs = ["VcsStateTest.java"],
    deps = [
        "//shared/java/com/google/idea/blaze/common/vcs",
        "@com_google_guava_guava//jar",
        "@junit//jar",
        "@truth//jar",
    ],
)
