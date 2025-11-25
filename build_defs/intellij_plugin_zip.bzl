load("@bazel_skylib//lib:paths.bzl", "paths")
load(":common.bzl", "intellij_common")

def _intellij_plugin_zip_impl(ctx):
    output = ctx.actions.declare_file(ctx.attr.filename)
    layout = intellij_common.compute_plugin_layout(ctx.attr.prefix, ctx.attr.deps)

    mapping = ["%s=%s" % (path, file.path) for path, file in layout.items()]

    ctx.actions.run(
        inputs = layout.values(),
        outputs = [output],
        mnemonic = "IntellijPluginZip",
        progress_message = "Creating intellij plugin zip for %{label}",
        executable = ctx.executable._zipper,
        arguments = ["c", output.path] + mapping,
    )

    return [DefaultInfo(files = depset([output]))]

intellij_plugin_zip = rule(
    implementation = _intellij_plugin_zip_impl,
    doc = """Creates an intellij deployable zip archive.

    All files of the dependencies are included in the `lib` directory and all runfiles are included
    from the root of the archive. See `intellij_common.compute_plugin_layout` for details on the
    layout.
    """,
    attrs = {
        "deps": attr.label_list(
            doc = "List of dependencies to be included in the zip.",
            allow_files = False,
            mandatory = True,
        ),
        "prefix": attr.string(
            doc = "The prefix inside the zip archive.",
            mandatory = True,
        ),
        "filename": attr.string(
            doc = "The name of the zip archive.",
            mandatory = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
            cfg = "exec",
        ),
    },
)
