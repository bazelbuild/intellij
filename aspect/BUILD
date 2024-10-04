#
# Description: Bazel aspect bundled with the Bazel IntelliJ plugin.
#

load(
    ":flag_hack.bzl",
    "define_flag_hack",
)

licenses(["notice"])

# Files needed at runtime for blaze-invoking integration tests
filegroup(
    name = "integration_test_files",
    srcs = [
        "artifacts.bzl",
        "intellij_info_impl.bzl",
        "java_classpath.bzl",
        "make_variables.bzl",
        ":BUILD.bazel",
        "//aspect/tools:CreateAar",
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
        "build_dependencies.bzl",
        "fast_build_info_bundled.bzl",
        "flag_hack.bzl",
        "intellij_info.bzl",
        "intellij_info_bundled.bzl",
        "intellij_info_impl_bundled.bzl",
        "java_classpath.bzl",
        "make_variables.bzl",
        ":BUILD.bazel",
        "//aspect/tools:CreateAar",
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

_dev_aspect_path = package_name()

# Aspect references have different label paths in development vs. when bundled with the plugin.
# This genrule is required for the aspect to be valid in all three repositories in which it's used.
# Changes:
#   //.../aspect/tools:tool_name -> //:tool_name_bin
#   //.../aspect:flag_hack -> //:flag_hack
#   :intellij_info_impl.bzl -> :intellij_info_impl_bundled.bzl
genrule(
    name = "modify_tools_path",
    srcs = ["intellij_info.bzl"],
    outs = ["intellij_info_bundled.bzl"],
    cmd = "cat $(SRCS) >$@ && " +
          "sed -i -e 's,//%s/tools:\" + tool_name,//:\" + tool_name + \"_bin\",g' $@ && " % _dev_aspect_path +
          "sed -i -e 's,//%s:flag_hack,//:flag_hack,g' $@  && " % _dev_aspect_path +
          "sed -i -e 's,:intellij_info_impl.bzl,:intellij_info_impl_bundled.bzl,g' $@",
)

# Removes internal only content from bundled aspect files as they cannot access
# by bazel no matter it's build from git repo or internal repo.
genrule(
    name = "create_intellij_info_impl_bundle",
    srcs = ["intellij_info_impl.bzl"],
    outs = ["intellij_info_impl_bundled.bzl"],
    cmd = "cat $(SRCS) >$@ && " +
          "sed -i -e '/BUNDLED-IGNORE-BEGIN/,/BUNDLED-IGNORE-END/d' $@ && " +
          "sed -i -e 's,load(\".*/rules_java/,load(\"@rules_java//', $@",
)

# Makes bundled fast_build_info.bzl use bundled intellij_info_impl.bzl
genrule(
    name = "create_fast_build_info_bundle",
    srcs = ["fast_build_info.bzl"],
    outs = ["fast_build_info_bundled.bzl"],
    cmd = "cat $(SRCS) >$@ && " +
          "sed -i -e 's,:intellij_info_impl.bzl,:intellij_info_impl_bundled.bzl,g' $@",
)

genrule(
    name = "create_workspace_file",
    outs = ["WORKSPACE"],
    srcs = [],
    cmd = r"""echo 'workspace(name = "intellij_aspect")' > $@"""
)

define_flag_hack()
