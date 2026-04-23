"""Rule that transitions a binary target to build with arbitrary copts."""

def _copt_transition_impl(settings, attr):
    return {"//command_line_option:copt": settings["//command_line_option:copt"] + attr.copts}

_copt_transition = transition(
    implementation = _copt_transition_impl,
    inputs = ["//command_line_option:copt"],
    outputs = ["//command_line_option:copt"],
)

def _copt_transition_binary_impl(ctx):
    # Outgoing transitions wrap attr.label in a list; unwrap it.
    binary = ctx.attr.binary[0] if type(ctx.attr.binary) == type([]) else ctx.attr.binary
    default_info = binary[DefaultInfo]
    original_executable = default_info.files_to_run.executable

    if not original_executable:
        fail("The 'binary' target must be executable")

    # Bazel requires the executable to be owned by this rule, so symlink it.
    new_executable = ctx.actions.declare_file(ctx.label.name + "/" + original_executable.basename)
    ctx.actions.symlink(
        output = new_executable,
        target_file = original_executable,
        is_executable = True,
    )

    result = [DefaultInfo(
        files = depset([new_executable]),
        runfiles = default_info.default_runfiles,
        executable = new_executable,
    )]

    if RunEnvironmentInfo in binary:
        result.append(binary[RunEnvironmentInfo])

    return result

copt_transition_binary = rule(
    implementation = _copt_transition_binary_impl,
    attrs = {
        "binary": attr.label(
            doc = "Executable target to transition with the specified copts.",
            allow_files = True,
            cfg = _copt_transition,
        ),
        "copts": attr.string_list(
            doc = "C compiler options to apply to the transitioned binary.",
            default = [],
        ),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
    executable = True,
)
