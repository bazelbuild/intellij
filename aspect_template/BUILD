filegroup(
    name = "aspect_files",
    srcs = [
        "WORKSPACE",
        "BUILD.bazel",
        "java_info.bzl",
        "java_info.template.bzl",
    ],
    visibility = ["//visibility:public"],
)

genrule(
    name = "generate_java_info_template",
    outs = ["java_info.template.bzl"],
    srcs = ["java_info.bzl"],
    cmd = "cat $(SRCS) >$@ && " +
        "sed -i -e '/TEMPLATE-IGNORE-BEGIN/,/TEMPLATE-IGNORE-END/d' $@ && " +
        "sed -i -e '/TEMPLATE-INCLUDE-BEGIN/,/TEMPLATE-INCLUDE-END/{s/^[#][#]//;}' $@",
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