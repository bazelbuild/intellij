load("//build_defs:intellij_integration_test.bzl", "intellij_integration_test")
load(":bazel_binary.bzl", "BazelBinary")
load(":project_archive.bzl", "project_archive")
load(":project_cache.bzl", "bazel_project_cache")

def _rlocation(ctx, file):
    if file.short_path.startswith("../"):
        return file.short_path[3:]
    else:
        return ctx.workspace_name + "/" + file.short_path

def _bazel_fixture_config_impl(ctx):
    output = ctx.actions.declare_file(ctx.label.name + ".prototext")

    bazel_binary = ctx.attr.bazel_binary[BazelBinary]

    input = proto.encode_text(struct(
        project_archive = _rlocation(ctx, ctx.file.project),
        repo_cache_archive = _rlocation(ctx, ctx.file.cache),
        bazel_binary = struct(version = bazel_binary.version, executable = _rlocation(ctx, bazel_binary.executable)),
    ))

    ctx.actions.write(output, input)

    return [DefaultInfo(
        files = depset([output]),
        runfiles = ctx.runfiles(files = [ctx.file.project, ctx.file.cache, bazel_binary.executable, output]),
    )]

bazel_fixture_config = rule(
    implementation = _bazel_fixture_config_impl,
    attrs = {
        "project": attr.label(
            allow_single_file = [".zip"],
            mandatory = True,
            doc = "Project archive (zip) to build.",
        ),
        "cache": attr.label(
            allow_single_file = [".zip"],
            mandatory = True,
            doc = "Repo cache archive (zip) to build.",
        ),
        "bazel_binary": attr.label(
            mandatory = True,
            providers = [BazelBinary],
            doc = "BazelBinary targets to build with.",
        ),
    },
)
