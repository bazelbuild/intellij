filegroup(
    name = "aspect_files",
    srcs = [
        "BUILD.bazel",
        "WORKSPACE",
        "code_generator_info.bzl",
        "code_generator_info.template.bzl",
        "java_info.bzl",
        "java_info.template.bzl",
    ],
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
    name = "generate_code_generator_info_template",
    srcs = ["code_generator_info.bzl"],
    outs = ["code_generator_info.template.bzl"],
    cmd = STRIP_TEMPLATE_SCRIPT,
)

genrule(
    name = "rename_files",
    srcs = ["BUILD.aspect"],
    outs = ["BUILD.bazel"],
    cmd = "cp $< $@",
)

genrule(
    name = "create_workspace_file",
    outs = ["WORKSPACE"],
    srcs = [],
    cmd = r"""echo 'workspace(name = "intellij_aspect_template")' > $@"""
)