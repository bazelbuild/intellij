BazelBinary = provider(
    doc = "Host Bazel executable and version.",
    fields = {
        "version": "str - Bazel release string (e.g., 8.6.0).",
        "executable": "File - Bazel executable used for running builds.",
    },
)

def _bazel_binary_impl(ctx):
    return [
        BazelBinary(
            version = ctx.attr.version,
            executable = ctx.file.executable,
        ),
        DefaultInfo(files = depset([ctx.file.executable])),
    ]

bazel_binary = rule(
    implementation = _bazel_binary_impl,
    attrs = {
        "version": attr.string(mandatory = True),
        "executable": attr.label(
            allow_single_file = True,
            cfg = "target",
            executable = True,
        ),
    },
    provides = [BazelBinary],
)
