load("@bazel_skylib//lib:paths.bzl", "paths")

def _resolve_target_artifact(rctx, target, source_dir):
    """Finds the generated artifact in the local bazel-bin directory."""
    return rctx.path(paths.join(
        source_dir,
        "bazel-bin",
        target.removeprefix("//").replace(":", "/"),
    ))

def _resolve_target_jar_name(target):
    """Derives the jar name from the target name."""
    return target.split(":")[1]

def _bazel_build_jars_impl(rctx):
    bazel = rctx.getenv("BAZEL_REAL")
    if not bazel:
        fail("BAZEL_REAL environment variable is not set, are you using bazelisk?")

    rctx.extract(rctx.path(rctx.attr.srcs), type = "tar.gz")
    source_dir = rctx.execute(["ls"]).stdout.strip()

    # windows requires non-hermetic build to avoid long paths issues :(
    if "windows" in rctx.os.name.lower():
        build_cmd = [bazel, "build"]
    else:
        build_cmd = [bazel, "--output_user_root=%s" % rctx.path("output"), "build"]

    for target in rctx.attr.jars:
        rctx.report_progress("building: %s" % target)
        result = rctx.execute(build_cmd + [target], working_directory = source_dir, timeout = 60 * 60 * 2)

        if result.return_code != 0:
            fail("could not build %s: %s" % (target, result.stderr))

        rctx.symlink(_resolve_target_artifact(rctx, target, source_dir), _resolve_target_jar_name(target))

    files = ", ".join(["'%s'" % _resolve_target_jar_name(target) for target in rctx.attr.jars])
    rctx.file("BUILD", content = "exports_files([%s])" % files)

bazel_build_jars = repository_rule(
    implementation = _bazel_build_jars_impl,
    attrs = {
        "srcs": attr.label(mandatory = True),
        "jars": attr.string_list(mandatory = True),
    },
)
