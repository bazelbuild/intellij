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
        "@diffutils//jar",
        "@truth//jar",
        "@truth8//jar",
    ],
)
