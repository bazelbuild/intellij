#
# Description: Bazel aspect bundled with the Bazel IntelliJ plugin.
#

licenses(["notice"])  # Apache 2.0

# Files needed at runtime for blaze-invoking integration tests
filegroup(
    name = "integration_test_files",
    srcs = [
        "artifacts.bzl",
        "intellij_info_impl.bzl",
        "java_classpath.bzl",
        "make_variables.bzl",
        ":BUILD.bazel",
        "//aspect/tools:JarFilter_deploy.jar",
        "//aspect/tools:PackageParser_deploy.jar",
    ],
    visibility = ["//visibility:public"],
)

# the aspect files that will be bundled with the final plugin zip
filegroup(
    name = "aspect_files",
    srcs = [
        "WORKSPACE",
        "artifacts.bzl",
        "fast_build_info.bzl",
        "intellij_info_bundled.bzl",
        "intellij_info_impl.bzl",
        "java_classpath.bzl",
        "make_variables.bzl",
        ":BUILD.bazel",
        "//aspect/tools:JarFilter_deploy.jar",
        "//aspect/tools:PackageParser_deploy.jar",
    ],
    visibility = ["//visibility:public"],
)

# BUILD file bundled with the aspect must not override the BUILD file
# used for development. So we name it BUILD.aspect, and rename prior
# to bundling with the plugin.
genrule(
    name = "rename_files",
    srcs = ["BUILD.aspect"],
    outs = ["BUILD.bazel"],
    cmd = "cp $< $@",
)

_dev_tools_path = "//aspect/tools"

# Aspect tools have different label paths in development vs. when bundled with the plugin.
# This genrule is required for the aspect to be valid in all three repositories in which it's used.
genrule(
    name = "modify_tools_path",
    srcs = ["intellij_info.bzl"],
    outs = ["intellij_info_bundled.bzl"],
    cmd = "cat $(SRCS) >$@ && " +
          "sed -i -e 's,%s:\" + tool_name,//:\" + tool_name + \"_bin\",g' $@" % _dev_tools_path,
)
