def _split_impl(settings, attr):
    return {
        "foo": {"//command_line_option:copt": ["-DFOO"]},
        "bar": {"//command_line_option:copt": ["-DBAR"]},
    }

split = transition(
    implementation = _split_impl,
    inputs = [],
    outputs = ["//command_line_option:copt"],
)

def _foo_bar_binary_impl(ctx):
    dep_by_split = ctx.split_attr.deps
    dep_foo = dep_by_split["foo"]
    dep_bar = dep_by_split["bar"]

    exe_foo = dep_foo[DefaultInfo].files_to_run.executable
    exe_bar = dep_bar[DefaultInfo].files_to_run.executable

    out_foo = ctx.actions.declare_file(ctx.label.name + "-foo")
    out_bar = ctx.actions.declare_file(ctx.label.name + "-bar")

    ctx.actions.symlink(output = out_foo, target_file = exe_foo)
    ctx.actions.symlink(output = out_bar, target_file = exe_bar)
    return [DefaultInfo(
        files = depset([out_foo, out_bar]),
        runfiles = ctx.runfiles(files = [out_foo, out_bar]),
    )]

foo_bar_binary = rule(
    implementation = _foo_bar_binary_impl,
    attrs = {
        # this deps is built *twice* via the split transition
        "deps": attr.label(
            cfg = split,
            providers = [DefaultInfo],
            doc = "Executable target (e.g., a cc_binary) to build for multiple defines.",
        ),
    },
)
