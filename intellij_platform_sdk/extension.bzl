load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

_IDE_URL_TEMPLATE = "https://www.jetbrains.com/intellij-repository/{repository}/com/jetbrains/intellij/{product}/{product}/{version}/{product}-{version}.zip"
_PLUGIN_URL_TEMPLATE = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/{plugin}/{version}/{plugin}-{version}.zip"

_sdk = tag_class(attrs = {
    "name": attr.string(mandatory = True),
    "alias": attr.string(),
    "version": attr.string(mandatory = True),
    "sha256": attr.string(mandatory = True),
    "build_file": attr.label(mandatory = True),
})

_plugin = tag_class(attrs = {
    "name": attr.string(mandatory = True),
    "alias": attr.string(),
    "version": attr.string(mandatory = True),
    "sha256": attr.string(mandatory = True),
    "build_file": attr.label(mandatory = True),
})

def _repo_name(tag):
    if tag.alias:
        return tag.alias

    (release, major, _) = tag.version.split(".")
    return "%s_%s_%s" % (tag.name, release, major)

def _collect_tags(mctx, tag_name):
    return [
        tag
        for mod in mctx.modules
        for tag in getattr(mod.tags, tag_name)
    ]

def _intellij_platform_extension_impl(mctx):
    for tag in _collect_tags(mctx, "sdk"):
        http_archive(
            name = _repo_name(tag),
            build_file = tag.build_file,
            sha256 = tag.sha256,
            url = _IDE_URL_TEMPLATE.format(
                product = tag.name,
                version = tag.version,
                repository = "snapshots" if "eap" in tag.version.lower() else "releases",
            ),
        )

    for tag in _collect_tags(mctx, "plugin"):
        http_archive(
            name = _repo_name(tag),
            build_file = tag.build_file,
            sha256 = tag.sha256,
            url = _PLUGIN_URL_TEMPLATE.format(
                plugin = tag.name,
                version = tag.version,
            ),
        )

intellij_platform = module_extension(
    implementation = _intellij_platform_extension_impl,
    tag_classes = {"sdk": _sdk, "plugin": _plugin},
)
