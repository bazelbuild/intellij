load(":bazel_versions.bzl", _bazel_versions = "bazel_versions")

_download = tag_class(attrs = {
    "versions": attr.string_list(),
})

def _collect_versions(mctx):
    return [
        version
        for mod in mctx.modules
        for tag in mod.tags.download
        for version in tag.versions
    ]

def _bazel_versions_impl(mctx):
    _bazel_versions(
        name = "bazel_versions",
        versions = _collect_versions(mctx),
    )

bazel_versions = module_extension(
    implementation = _bazel_versions_impl,
    tag_classes = {
        "download": _download,
    },
)
