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
    name = "LabelTest",
    size = "small",
    srcs = ["LabelTest.java"],
    deps = [
        "//shared/java/com/google/idea/blaze/common",
        "@junit//jar",
        "@truth//jar",
    ],
)
