load("@bazel_skylib//lib:paths.bzl", "paths")

_BAZEL_URL_TEMPLATE = "https://codeload.github.com/bazelbuild/bazel/tar.gz/refs/tags/{tag}"
_BAZEL_SOURCE_DIR = "sources"

def _resolve_target_artifact(rctx, target):
    """Finds the generated artifact in the local bazel-bin directory."""
    return rctx.path(paths.join(
        _BAZEL_SOURCE_DIR,
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

    rctx.download_and_extract(
        url = _BAZEL_URL_TEMPLATE.format(tag = rctx.attr.tag),
        sha256 = rctx.attr.sha256,
        type = "tar.gz",
        strip_prefix = "bazel-" + rctx.attr.tag,
        output = _BAZEL_SOURCE_DIR,
    )

    # windows requires non-hermetic build to avoid long paths issues :(
    if "windows" in rctx.os.name.lower():
        build_cmd = [bazel, "build"]
    else:
        # prevent external RC files from overriding --output_user_root (like on GH CI)
        build_cmd = [bazel, "--nohome_rc", "--output_user_root=%s" % rctx.path("output"), "build"]

    for target in rctx.attr.jars:
        rctx.report_progress("building: %s" % target)
        result = rctx.execute(build_cmd + [target], working_directory = _BAZEL_SOURCE_DIR, timeout = 1800)

        if result.return_code != 0:
            fail("could not build %s: %s" % (target, result.stderr))

        rctx.symlink(_resolve_target_artifact(rctx, target), _resolve_target_jar_name(target))

    files = ", ".join(["'%s'" % _resolve_target_jar_name(target) for target in rctx.attr.jars])
    rctx.file("BUILD", content = "exports_files([%s])" % files)

bazel_build_jars = repository_rule(
    implementation = _bazel_build_jars_impl,
    attrs = {
        "sha256": attr.string(mandatory = True),
        "tag": attr.string(mandatory = True),
        "jars": attr.string_list(mandatory = True),
    },
)
