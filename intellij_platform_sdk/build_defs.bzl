"""Convenience methods for plugin_api."""

load("@rules_java//java:defs.bzl", "java_import")

# The current indirect ij_product mapping (eg. "intellij-latest")
INDIRECT_IJ_PRODUCTS = {
    "clion-oss-oldest-stable": "clion-2025.2",
    "clion-oss-latest-stable": "clion-2025.3",
    "clion-oss-under-dev": "clion-2025.3",
}

(CHANNEL_STABLE, CHANNEL_BETA, CHANNEL_CANARY, CHANNEL_FREEFORM) = ("stable", "beta", "canary", "freeform")

INDIRECT_PRODUCT_CHANNELS = {
    # Channel mapping for Bazel Plugin OSS
    "clion-oss-oldest-stable": CHANNEL_STABLE,
    "clion-oss-latest-stable": CHANNEL_STABLE,
    "clion-oss-under-dev": CHANNEL_CANARY,
}

def _check_channel_map():
    if INDIRECT_PRODUCT_CHANNELS.keys() != INDIRECT_IJ_PRODUCTS.keys():
        fail("Key mismatch between INDIRECT_PRODUCT_CHANNELS and INDIRECT_IJ_PRODUCTS: missing: %s extra: %s" % (
            [k for k in INDIRECT_IJ_PRODUCTS.keys() if k not in INDIRECT_PRODUCT_CHANNELS.keys()],
            [k for k in INDIRECT_PRODUCT_CHANNELS.keys() if k not in INDIRECT_IJ_PRODUCTS.keys()],
        ))
    unexpected = [
        (k, v)
        for k, v in INDIRECT_PRODUCT_CHANNELS.items()
        if v not in [CHANNEL_STABLE, CHANNEL_BETA, CHANNEL_CANARY]
    ]
    if unexpected:
        fail("Unexpected values in INDIRECT_PRODUCT_CHANNELS: %s" % unexpected)

def _build_ij_product(ide, directory, version):
    return struct(
        ide = ide,
        directory = "%s_%s" % (directory, version.replace(".", "_")),
        version = version,
    )

def _build_ij_product_dict(versions):
    result = {}
    for version in versions:
        result["clion-%s" % version] = _build_ij_product("clion", "clion", version)

    return result

DIRECT_IJ_PRODUCTS = _build_ij_product_dict(["2025.2", "2025.3"])

def _do_select_for_plugin_api(params):
    """A version of select_for_plugin_api which accepts indirect products."""
    if not params:
        fail("Empty select_for_plugin_api")

    expanded_params = dict(**params)

    # Expand all indirect plugin_apis to point to their
    # corresponding direct plugin_api.
    #
    # {"intellij-2016.3.1": "foo"} ->
    # {"intellij-2016.3.1": "foo", "intellij-latest": "foo"}
    fallback_value = None
    for indirect_ij_product, resolved_plugin_api in INDIRECT_IJ_PRODUCTS.items():
        if resolved_plugin_api in params:
            expanded_params[indirect_ij_product] = params[resolved_plugin_api]
            if not fallback_value:
                fallback_value = params[resolved_plugin_api]
        if indirect_ij_product in params:
            expanded_params[resolved_plugin_api] = params[indirect_ij_product]

    # Map the shorthand ij_products to full config_setting targets.
    # This makes it more convenient so the user doesn't have to
    # fully specify the path to the plugin_apis
    select_params = dict()
    for ij_product, value in expanded_params.items():
        if ij_product == "default":
            select_params["//conditions:default"] = value
        else:
            select_params["//intellij_platform_sdk:" + ij_product] = value

    return select(
        select_params,
        no_match_error = "define an intellij product version, e.g. --define=ij_product=clion-oss-latest",
    )

def select_for_version(versions, default = []):
    """Selects for the supported IDEs.

    Args:
        version: Map from version to File list.
        default: Files to use for any version not passed.
    Returns:
        A select statement on all versions to select a list of files.

    Example:
      java_library(
        name = "foo",
        srcs = select_for_version(
            versions = { "2024.1": [":2024_1_only_sources"] },
            default = [":default_sources"],
        ),
      )
    """

    params = dict()
    for ij_product, value in DIRECT_IJ_PRODUCTS.items():
        if value.version in versions:
            params[ij_product] = versions[value.version]
    params["default"] = default

    return _do_select_for_plugin_api(params)

def _plugin_api_directory(value):
    return "@" + value.directory + "//"

def select_from_plugin_api_directory(targets):
    """Internal convenience method to generate select statement from the IDE's plugin_api directories.

    Args:
      intellij: the items that IntelliJ product to use.
      clion: the items that clion product to use.
      intellij_ue: the items that intellij ue product to use.

    Returns:
      a select statement map from DIRECT_IJ_PRODUCTS to items that they should use.

    """

    # Map (direct ij_product) -> corresponding product directory
    params = dict()
    for ij_product, value in DIRECT_IJ_PRODUCTS.items():
        params[ij_product] = [_plugin_api_directory(value) + item for item in targets]

    return _do_select_for_plugin_api(params)

def select_build_name():
    """Returns a select for name of the current build target, e.g. clion-2025.2"""

    params = dict()
    for ij_product, value in DIRECT_IJ_PRODUCTS.items():
        params[ij_product] = ["%s-%s" % (value.ide, value.version)]

    return _do_select_for_plugin_api(params)

def no_mockito_extensions(name, jars, **kwargs):
    """Removes mockito extensions from jars.

    Args:
        name: Name of the resulting java_import target.
        jars: List of jars from which to remove mockito extensions.
        **kwargs: Arbitrary attributes for the java_import target.
    """

    output_jars = []
    for input_jar in jars:
        output_jar_name = name + "_" + input_jar.replace("/", "_")
        output_jar = name + "/" + input_jar
        native.genrule(
            name = output_jar_name,
            srcs = [input_jar],
            outs = [output_jar],
            cmd = """
            tmpdir=$$(mktemp -d)
            zipper="$$(pwd)/$(execpath @bazel_tools//tools/zip:zipper)"
            "$$zipper" x "$<" -d ".out"
            mv ".out" "$$tmpdir"

            pushd "$$tmpdir/.out" >/dev/null
            rm -fr "mockito-extensions"

            # We store the results from `find` in a file to deal with filenames with spaces
            files_to_tar_file=$$(mktemp)
            find . -type f | sed 's:^./::' > "$${files_to_tar_file}"

            "$$zipper" cC "../out.jar" "@$${files_to_tar_file}"
            popd

            cp "$$tmpdir/out.jar" "$@"
            chmod u+rw "$@"
            """,
            tools = ["@bazel_tools//tools/zip:zipper"],
        )
        output_jars.append(output_jar_name)
    java_import(
        name = name,
        jars = output_jars,
        **kwargs
    )

# Since 2022.3, JVM 17 is required to start IntelliJ
# https://blog.jetbrains.com/platform/2022/08/intellij-project-migrates-to-java-17/
def java_version_flags():
    return ["-source", "17", "-target", "17"]

def select_for_channel(channel_map):
    """Returns a select based on the IDE channel (stable, beta, canary).

    Args:
      channel_map: a dict with keys "stable", "beta" and "canary". The rest of targets will be considered "freeform"

    Returns:
      A select that will select values from channel_map based on the build config.
    """
    _check_channel_map()
    if channel_map.keys() != [CHANNEL_STABLE, CHANNEL_BETA, CHANNEL_CANARY, CHANNEL_FREEFORM]:
        fail("channel_map must contain exactly %s, %s and %s" % (CHANNEL_STABLE, CHANNEL_BETA, CHANNEL_CANARY, CHANNEL_FREEFORM))
    select_map = {
        ("//intellij_platform_sdk:%s" % indirect_product): channel_map[channel]
        for indirect_product, channel in INDIRECT_PRODUCT_CHANNELS.items()
    }

    # We reverse INDIRECT_IJ_PRODUCTS.items() here to that the inverse map contains
    # the first occurrence of any value that is duplicated, not the last:
    inverse_ij_products = {v: k for k, v in reversed(INDIRECT_IJ_PRODUCTS.items())}

    # Add directly specified IDE versions which some builds use:
    select_map.update(
        {
            ("//intellij_platform_sdk:%s" % direct_product): channel_map[INDIRECT_PRODUCT_CHANNELS[indirect_product]]
            for direct_product, indirect_product in inverse_ij_products.items()
        },
    )

    # Some IDE versions are not in a channel, but users would still like to build and test them:
    select_map.update(
        {
            ("//intellij_platform_sdk:%s" % direct_product): channel_map[CHANNEL_FREEFORM]
            for direct_product in DIRECT_IJ_PRODUCTS.keys()
            if direct_product not in inverse_ij_products
        },
    )

    select_map.update({"//conditions:default": channel_map[CHANNEL_STABLE]})

    return select(select_map)
