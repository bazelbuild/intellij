load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

_PRODUCT_URL_TEMPLATE = "https://www.jetbrains.com/intellij-repository/{repository}/com/jetbrains/intellij/{product}/{product}/{version}/{product}-{version}.zip"
_MAVEN_URL_TEMPLATE = "https://www.jetbrains.com/intellij-repository/{repository}/com/jetbrains/intellij/{repo}/{name}/{version}/{name}-{version}.jar"
_PLUGIN_URL_TEMPLATE = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/{plugin}/{version}/{plugin}-{version}.zip"

def _guess_repository_name(rctx):
    """Guesses whether the release or snapshots repository should be used based on the version name."""
    return "snapshots" if "eap" in rctx.attr.version.lower() else "releases"

def _download_product(rctx):
    """Downloads the sdk and parses the product-info.json file."""
    rctx.download_and_extract(
        url = _PRODUCT_URL_TEMPLATE.format(
            product = rctx.attr.product,
            version = rctx.attr.version,
            repository = _guess_repository_name(rctx),
        ),
        sha256 = rctx.attr.sha256,
    )

    return json.decode(rctx.read("product-info.json"))

def _download_maven_libraries(rctx, product_version):
    """Parses the BUILD file and downloads all #MAVEN libraries."""
    directive = "#MAVEN:"
    content = rctx.read(rctx.attr.build_file)

    for line in content.split("\n"):
        start = line.find(directive)
        if start < 0:
            continue

        repo, name = line[start + len(directive):].split(":")

        rctx.download(
            url = _MAVEN_URL_TEMPLATE.format(
                name = name,
                repo = repo,
                version = product_version,
                repository = _guess_repository_name(rctx),
            ),
            output = "maven/%s.jar" % name,
        )

def _intellij_sdk_impl(rctx):
    product = _download_product(rctx)
    _download_maven_libraries(rctx, product["buildNumber"])

    rctx.symlink(rctx.attr.build_file, "BUILD.bazel")

_intellij_sdk = repository_rule(
    implementation = _intellij_sdk_impl,
    attrs = {
        "product": attr.string(mandatory = True),
        "version": attr.string(mandatory = True),
        "sha256": attr.string(mandatory = True),
        "build_file": attr.label(mandatory = True),
    },
)

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
        _intellij_sdk(
            name = _repo_name(tag),
            build_file = tag.build_file,
            sha256 = tag.sha256,
            product = tag.name,
            version = tag.version,
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
