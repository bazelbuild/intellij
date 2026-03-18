load(":bazel_binary.bzl", "BazelBinary")

def _bazel_project_cache_impl(ctx):
    output = ctx.actions.declare_file(ctx.label.name + ".zip")

    bazel_binaries = [it[BazelBinary] for it in ctx.attr.bazel_versions]

    input = proto.encode_text(struct(
        output = output.path,
        project = ctx.file.project.path,
        bazel_binaries = [struct(version = it.version, executable = it.executable.path) for it in bazel_binaries],
        targets = ctx.attr.targets,
        flags = ctx.attr.flags or [],
    ))

    ctx.actions.run(
        executable = ctx.executable._builder,
        arguments = [input],
        inputs = [ctx.file.project] + [it.executable for it in bazel_binaries],
        outputs = [output],
        execution_requirements = {"requires-network": "1"},
        use_default_shell_env = True,
        mnemonic = "BazelProjectCache",
        progress_message = "Building repository cache for %s" % ctx.label,
    )

    return [DefaultInfo(files = depset([output]))]

bazel_project_cache = rule(
    implementation = _bazel_project_cache_impl,
    attrs = {
        "project": attr.label(
            allow_single_file = [".zip"],
            mandatory = True,
            doc = "Project archive (zip) to build.",
        ),
        "targets": attr.string_list(
            default = ["//..."],
            doc = "Bazel targets to fetch dependencies for.",
        ),
        "flags": attr.string_list(
            doc = "Additional Bazel build flags.",
        ),
        "bazel_versions": attr.label_list(
            mandatory = True,
            providers = [BazelBinary],
            doc = "BazelBinary targets to build with.",
        ),
        "_builder": attr.label(
            cfg = "exec",
            default = Label("//testing/src/com/google/idea/testing/bazel:cache_bin"),
            executable = True,
        ),
    },
)
