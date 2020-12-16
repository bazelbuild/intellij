package(default_visibility = ["//java:__subpackages__"])

java_library(name = "empty")

# Getting tools.jar from @local_jdk//:langtools is deprecated. Instead,
# copy tools.jar from @bazel_tools//tools_jdk:current_java_runtime.
# https://github.com/bazelbuild/bazel/issues/5594
# https://stackoverflow.com/questions/53066974/how-can-i-use-the-jar-tool-with-bazel-v0-19
#
# tools.jar no longer exists separately after jdk9, in that case we just use an
# empty jar for compatibility.
genrule(
    name = "jdk_tools_jar",
    srcs = [":empty"],
    outs = ["tools.jar"],
    cmd = """
        if [ -f $(JAVABASE)/lib/tools.jar ]; then
            cp $(JAVABASE)/lib/tools.jar $@
        else
            cp $(location :empty) $@
        fi
    """,
    toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
    tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
)

java_import(
    name = "langtools",
    jars = [":tools.jar"],
)
