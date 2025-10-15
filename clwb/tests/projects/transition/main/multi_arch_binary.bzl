def _arch_split_impl(settings, attr):
    # Each key identifies a branch of the split. We set --platforms per-branch.
    return {
        "amd64": {"//command_line_option:platforms": ["@zig_sdk//platform:linux_amd64"]},
        "arm64": {"//command_line_option:platforms": ["@zig_sdk//platform:linux_arm64"]},
    }

arch_split = transition(
    implementation = _arch_split_impl,
    inputs = [],
    outputs = ["//command_line_option:platforms"],
)

def _multi_arch_binary_impl(ctx):
    # Each split branch gives us the dep *as built for that platform*.
    dep_by_arch = ctx.split_attr.deps
    dep_amd = dep_by_arch["amd64"]
    dep_arm = dep_by_arch["arm64"]

    # Grab the actual executables (works with cc_binary and other executable rules).
    exe_amd = dep_amd[DefaultInfo].files_to_run.executable
    exe_arm = dep_arm[DefaultInfo].files_to_run.executable

    # User-provided output filenames:
    out_amd = ctx.outputs.out_amd64
    out_arm = ctx.outputs.out_arm64

    # Make stable, cacheable symlinks so the outputs live under bazel-bin with your names.
    ctx.actions.symlink(output = out_amd, target_file = exe_amd)
    ctx.actions.symlink(output = out_arm, target_file = exe_arm)

    return [DefaultInfo(
        files = depset([out_amd, out_arm]),
        runfiles = ctx.runfiles(files = [out_amd, out_arm]),
    )]

multi_arch_binary = rule(
    implementation = _multi_arch_binary_impl,
    attrs = {
        # This dep is built *twice* via the split transition.
        "deps": attr.label(
            cfg = arch_split,
            providers = [DefaultInfo],
            doc = "Executable target (e.g., a cc_binary) to build for multiple arches.",
        ),
        # Named outputs (caller picks the filenames).
        "out_amd64": attr.output(mandatory = True),
        "out_arm64": attr.output(mandatory = True),
    },
    doc = "Build the same executable for amd64 and arm64, and expose both outputs.",
)
