# Repackage aspect files for testing, similar to what is done when generating the final zip file.

def _repacked_files_impl(ctx):
    outputs = []

    for target in ctx.attr.srcs:
        for file in target.files.to_list():
            link = ctx.actions.declare_file("%s/%s" % (ctx.attr.prefix, file.basename))
            outputs.append(link)

            ctx.actions.symlink(output = link, target_file = file)

    return [DefaultInfo(files = depset(outputs))]

repacked_files = rule(
    implementation = _repacked_files_impl,
    attrs = {
        "srcs": attr.label_list(mandatory = True),
        "prefix": attr.string(mandatory = True),
    },
)
