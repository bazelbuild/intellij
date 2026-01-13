load("@bazel_skylib//lib:paths.bzl", "paths")

_BAZEL_URL_TEMPLATE = "https://github.com/bazelbuild/bazel/archive/refs/tags/{version}.zip"
_BAZLE_REPO_DIR = "repo"

def _resolve_target_artifact(rctx, target):
    """Finds the generated artifact in the local bazel-bin directory."""
    return rctx.path(paths.join(
        _BAZLE_REPO_DIR,
        "bazel-bin",
        target.removeprefix("//").replace(":", "/"),
    ))

def _resolve_target_jar_name(target):
    """Derives the jar name from the target name."""
    return target.split(":")[1]

def _bazel_impl(rctx):
    bazel = rctx.getenv("BAZEL_REAL")
    if not bazel:
        fail("BAZEL_REAL environment variable is not set, are you using bazelisk?")

    rctx.download_and_extract(
        _BAZEL_URL_TEMPLATE.format(version = rctx.attr.version),
        strip_prefix = "bazel-%s" % rctx.attr.version,
        sha256 = rctx.attr.sha256,
        output = _BAZLE_REPO_DIR,
    )

    # windows requires non-hermetic build to avoid long paths issues :(
    if "windows" in rctx.os.name.lower():
        build_cmd = [bazel, "build"]
    else:
        build_cmd = [bazel, "--output_user_root=%s" % rctx.path("output"), "build"]

    for target in rctx.attr.jars:
        result = rctx.execute(
            build_cmd + [target],
            working_directory = _BAZLE_REPO_DIR,
        )

        if result.return_code != 0:
            fail("could not build %s: %s" % (target, result.stderr))

        rctx.symlink(_resolve_target_artifact(rctx, target), _resolve_target_jar_name(target))

    files = ", ".join(["'%s'" % _resolve_target_jar_name(target) for target in rctx.attr.jars])
    rctx.file("BUILD", content = "exports_files([%s])" % files)

bazel_source_jars = repository_rule(
    implementation = _bazel_impl,
    attrs = {
        "version": attr.string(mandatory = True),
        "sha256": attr.string(mandatory = True),
        "jars": attr.string_list(mandatory = True),
    },
)
