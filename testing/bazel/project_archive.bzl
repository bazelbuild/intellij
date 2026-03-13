load("@bazel_skylib//lib:paths.bzl", "paths")

def _realpath(base_path, file):
    return paths.relativize(file.short_path, base_path)

def _project_archive_impl(ctx):
    base = "%s/%s" % (ctx.label.package, ctx.attr.strip_prefix) if ctx.attr.strip_prefix else ctx.label.package

    mapping = [
        "%s=%s" % (_realpath(base, file), file.path)
        for file in ctx.files.srcs
    ]

    archive = ctx.actions.declare_file(ctx.label.name + ".zip")
    ctx.actions.run(
        inputs = ctx.files.srcs,
        executable = ctx.executable._zipper,
        outputs = [archive],
        arguments = ["c", archive.path] + mapping,
    )

    return [DefaultInfo(files = depset([archive]))]

project_archive = rule(
    implementation = _project_archive_impl,
    attrs = {
        "srcs": attr.label_list(
            allow_files = True,
            mandatory = True,
        ),
        "strip_prefix": attr.string(
            default = "",
            doc = "Directory prefix to strip from all file paths inside the archive.",
        ),
        "_zipper": attr.label(
            cfg = "exec",
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
        ),
    },
)
