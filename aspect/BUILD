#
# Description: Bazel aspect bundled with the Bazel IntelliJ plugin.
#

load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load(":build_defs.bzl", "aspect_files", "aspect_library")
load(":flag_hack.bzl", "define_flag_hack")

licenses(["notice"])

# the aspect files that will be bundled with the final plugin zip
aspect_library(
    name = "aspect_lib",
    files = [
        ":aspect_files",
        ":aspect_tools",
    ],
    namespace = "aspect/default",
    visibility = ["//visibility:public"],
)

# Separate different targets as they are put into different directories
aspect_files(
    name = "aspect_files",
    files = [
        "artifacts.bzl",
        "cc_dependencies.bzl",
        "fast_build_info_bundled.bzl",
        "flag_hack.bzl",
        "intellij_info_impl_bundled.bzl",
        "java_classpath.bzl",
        "make_variables.bzl",
        "xcode_query.bzl",  # maybe move this to a dedicated library, but let's keep this here for now
        ":BUILD.bazel",
    ],
)


aspect_files(
    name = "aspect_tools",
    jars = [
        "//aspect/tools:JarFilter_deploy.jar",
        "//aspect/tools:PackageParser_deploy.jar",
        "//aspect/tools:CreateAar_deploy.jar",
    ],
    namespace = "tools",
)

aspect_library(
    name = "aspect_qsync_lib",
    files = [
        "build_compose_dependencies.bzl",
        "build_dependencies.bzl",
        "build_dependencies_android_deps.bzl",
        "build_dependencies_deps.bzl",
    ],
    namespace = "aspect/qsync",
    visibility = ["//visibility:public"],
)

aspect_library(
    name = "aspect_modules",
    files = glob(["modules/*.bzl"]),
    namespace = "aspect/modules",
    visibility = ["//visibility:public"],
)

# BUILD file bundled with the aspect must not override the BUILD file
# used for development. So we name it BUILD.aspect, and rename prior
# to bundling with the plugin.

copy_file(
    name = "rename_files",
    src = "BUILD.aspect",
    out = "BUILD.bazel",
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
          "sed -i -e 's,//%s/tools:\" + tool_name,tools/\" + tool_name,g' $@ && " % _dev_aspect_path +
          "sed -i -e 's,//%s:flag_hack,:flag_hack,g' $@  && " % _dev_aspect_path +
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
          "sed -i -e '/BUNDLED-INCLUDE-BEGIN/,/BUNDLED-INCLUDE-END/{s/^[#][#]//;}' $@ &&" +
          "sed -i -e 's,load(\"//aspect_template:,load(\"ASPECT_TEMPLATE_PATH', $@ &&" +
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

define_flag_hack()

aspect_library(
    name = "aspect_template_lib",
    files = [
        "cc_info.bzl",
        "cc_info.template.bzl",
        "code_generator_info.bzl",
        "code_generator_info.template.bzl",
        "intellij_info.template.bzl",
        "intellij_info_bundled.bzl",
        "java_info.bzl",
        "java_info.template.bzl",
        "python_info.bzl",
        "python_info.template.bzl",
    ],
    namespace = "aspect/template",
    visibility = ["//visibility:public"],
)

STRIP_TEMPLATE_SCRIPT = """cat $(SRCS) >$@ &&
sed -i -e '/TEMPLATE-IGNORE-BEGIN/,/TEMPLATE-IGNORE-END/d' $@ &&
sed -i -e '/TEMPLATE-INCLUDE-BEGIN/,/TEMPLATE-INCLUDE-END/{s/^[#][#]//;}' $@
"""

genrule(
    name = "generate_java_info_template",
    srcs = ["java_info.bzl"],
    outs = ["java_info.template.bzl"],
    cmd = STRIP_TEMPLATE_SCRIPT,
)

genrule(
    name = "generate_py_info_template",
    srcs = ["python_info.bzl"],
    outs = ["python_info.template.bzl"],
    cmd = STRIP_TEMPLATE_SCRIPT,
)

genrule(
    name = "generate_code_generator_info_template",
    srcs = ["code_generator_info.bzl"],
    outs = ["code_generator_info.template.bzl"],
    cmd = STRIP_TEMPLATE_SCRIPT,
)

genrule(
    name = "generate_intellij_info_template",
    srcs = ["intellij_info_bundled.bzl"],
    outs = ["intellij_info.template.bzl"],
    cmd = STRIP_TEMPLATE_SCRIPT,
)

genrule(
    name = "generate_cc_info_template",
    srcs = ["cc_info.bzl"],
    outs = ["cc_info.template.bzl"],
    cmd = STRIP_TEMPLATE_SCRIPT,
)
