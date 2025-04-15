load("@rules_java//java:java_import.bzl", "java_import")
load("@rules_java//java:java_library.bzl", "java_library")

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
