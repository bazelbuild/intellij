filegroup(
    name = "aspect_files",
    srcs = [
        "WORKSPACE",
        "BUILD.bazel",
        "java_info.bzl",
    ],
    visibility = ["//visibility:public"],
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