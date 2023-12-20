package(default_visibility = ["//visibility:public"])

# The JDI parts of tools.jar.
java_import(
    name = "jpda-jdi",
    jars = ["jdk8/jpda-jdi.jar"],
)

java_library(
    name = "python",
    exports = ["//third_party/python"],
)

java_library(
    name = "truth",
    exports = [
        "@com_googlecode_java_diff_utils_diffutils//jar",
        "@com_google_truth_truth//jar",
        "@com_google_truth_extensions_truth_java8_extension//jar",
    ],
)
