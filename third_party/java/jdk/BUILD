package(default_visibility = ["//java:__subpackages__"])

# Getting tools.jar from @local_jdk//:langtools is deprecated. Instead,
# copy tools.jar from @bazel_tools//tools_jdk:current_java_runtime.
# https://github.com/bazelbuild/bazel/issues/5594
# https://stackoverflow.com/questions/53066974/how-can-i-use-the-jar-tool-with-bazel-v0-19
genrule(
    name = "jdk_tools_jar",
    outs = ["tools.jar"],
    cmd = "cp $(JAVABASE)/lib/tools.jar $@",
    toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
    tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
)

java_import(
    name = "langtools",
    jars = [":tools.jar"],
)
