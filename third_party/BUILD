package(default_visibility = ["//visibility:public"])

# The JDI parts of tools.jar.
java_import(
    name = "jpda-jdi",
    jars = ["jdk8/jpda-jdi.jar"],
)

sh_binary(
    name = "zip",
    srcs = ["zip-wrap/zip.sh"],
)
