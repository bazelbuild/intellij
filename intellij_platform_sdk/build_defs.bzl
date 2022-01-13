"""Convenience methods for plugin_api."""

# The current indirect ij_product mapping (eg. "intellij-latest")
INDIRECT_IJ_PRODUCTS = {
    # Indirect ij_product mapping for internal Blaze Plugin
    "intellij-latest": "intellij-2021.2",
    "intellij-latest-mac": "intellij-2021.2-mac",
    "intellij-beta": "intellij-2021.2",
    "intellij-under-dev": "intellij-2021.3",
    "intellij-ue-latest": "intellij-ue-2021.2",
    "intellij-ue-latest-mac": "intellij-ue-2021.2-mac",
    "intellij-ue-beta": "intellij-ue-2021.2",
    "intellij-ue-under-dev": "intellij-ue-2021.3",
    "android-studio-latest": "android-studio-2020.3",
    "android-studio-beta": "android-studio-2020.3",
    "android-studio-beta-mac": "android-studio-2020.3-mac",
    "android-studio-canary": "android-studio-2021.1",
    "clion-latest": "clion-2021.2",
    "clion-latest-mac": "clion-2021.2-mac",
    "clion-beta": "clion-2021.2",
    "clion-under-dev": "clion-2021.2",
    # Indirect ij_product mapping for Bazel Plugin OSS
    "intellij-oss-stable": "intellij-2021.2",
    "intellij-oss-beta": "intellij-2021.3",
    "intellij-oss-under-dev": "intellij-2021.3",
    "intellij-ue-oss-stable": "intellij-ue-2021.2",
    "intellij-ue-oss-beta": "intellij-ue-2021.3",
    "intellij-ue-oss-under-dev": "intellij-ue-2021.3",
    "android-studio-oss-stable": "android-studio-2020.3",
    "android-studio-oss-beta": "android-studio-2020.3",
    "android-studio-oss-under-dev": "android-studio-2021.1",
    "clion-oss-stable": "clion-2021.2",
    "clion-oss-beta": "clion-2021.3",
    "clion-oss-under-dev": "clion-2021.3",
}

DIRECT_IJ_PRODUCTS = {
    "intellij-2020.3": struct(
        ide = "intellij",
        directory = "intellij_ce_2020_3",
    ),
    "intellij-2020.3-mac": struct(
        ide = "intellij",
        directory = "intellij_ce_2020_3",
    ),
    "intellij-2021.1": struct(
        ide = "intellij",
        directory = "intellij_ce_2021_1",
    ),
    "intellij-2021.1-mac": struct(
        ide = "intellij",
        directory = "intellij_ce_2021_1",
    ),
    "intellij-2021.2": struct(
        ide = "intellij",
        directory = "intellij_ce_2021_2",
    ),
    "intellij-2021.2-mac": struct(
        ide = "intellij",
        directory = "intellij_ce_2021_2",
    ),
    "intellij-2021.3": struct(
        ide = "intellij",
        directory = "intellij_ce_2021_3",
    ),
    "intellij-2021.3-mac": struct(
        ide = "intellij",
        directory = "intellij_ce_2021_3",
    ),
    "intellij-ue-2020.3": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2020_3",
    ),
    "intellij-ue-2020.3-mac": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2020_3",
    ),
    "intellij-ue-2021.1": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2021_1",
    ),
    "intellij-ue-2021.1-mac": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2021_1",
    ),
    "intellij-ue-2021.2": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2021_2",
    ),
    "intellij-ue-2021.2-mac": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2021_2",
    ),
    "intellij-ue-2021.3": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2021_3",
    ),
    "intellij-ue-2021.3-mac": struct(
        ide = "intellij-ue",
        directory = "intellij_ue_2021_3",
    ),
    "android-studio-2020.3": struct(
        ide = "android-studio",
        directory = "android_studio_2020_3",
    ),
    "android-studio-2020.3-mac": struct(
        ide = "android-studio",
        directory = "android_studio_2020_3",
    ),
    "android-studio-2021.1": struct(
        ide = "android-studio",
        directory = "android_studio_2021_1",
    ),
    "android-studio-2021.2": struct(
        ide = "android-studio",
        directory = "android_studio_2021_2",
    ),
    "clion-2020.3": struct(
        ide = "clion",
        directory = "clion_2020_3",
    ),
    "clion-2020.3-mac": struct(
        ide = "clion",
        directory = "clion_2020_3",
    ),
    "clion-2021.1": struct(
        ide = "clion",
        directory = "clion_2021_1",
    ),
    "clion-2021.1-mac": struct(
        ide = "clion",
        directory = "clion_2021_1",
    ),
    "clion-2021.2": struct(
        ide = "clion",
        directory = "clion_2021_2",
    ),
    "clion-2021.2-mac": struct(
        ide = "clion",
        directory = "clion_2021_2",
    ),
    "clion-2021.3": struct(
        ide = "clion",
        directory = "clion_2021_3",
    ),
    "clion-2021.3-mac": struct(
        ide = "clion",
        directory = "clion_2021_3",
    ),
}

def select_for_plugin_api(params):
    """Selects for a plugin_api.

    Args:
        params: A dict with ij_product -> value.
                You may only include direct ij_products here,
                not indirects (eg. intellij-latest).
    Returns:
        A select statement on all plugin_apis. Unless you include a "default",
        a non-matched plugin_api will result in an error.

    Example:
      java_library(
        name = "foo",
        srcs = select_for_plugin_api({
            "intellij-2016.3.1": [...my intellij 2016.3 sources ....],
            "intellij-2012.2.4": [...my intellij 2016.2 sources ...],
        }),
      )
    """
    for indirect_ij_product in INDIRECT_IJ_PRODUCTS:
        if indirect_ij_product in params:
            error_message = "".join([
                "Do not select on indirect ij_product %s. " % indirect_ij_product,
                "Instead, select on an exact ij_product.",
            ])
            fail(error_message)
    return _do_select_for_plugin_api(params)

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
        no_match_error = "define an intellij product version, e.g. --define=ij_product=intellij-latest",
    )

def select_for_ide(intellij = None, intellij_ue = None, android_studio = None, clion = None, default = []):
    """Selects for the supported IDEs.

    Args:
        intellij: Files to use for IntelliJ. If None, will use default.
        intellij_ue: Files to use for IntelliJ UE. If None, will use value chosen for 'intellij'.
        android_studio: Files to use for Android Studio. If None will use default.
        clion: Files to use for CLion. If None will use default.
        default: Files to use for any IDEs not passed.
    Returns:
        A select statement on all plugin_apis to lists of files, sorted into IDEs.

    Example:
      java_library(
        name = "foo",
        srcs = select_for_ide(
            clion = [":cpp_only_sources"],
            default = [":java_only_sources"],
        ),
      )
    """
    intellij = intellij if intellij != None else default
    intellij_ue = intellij_ue if intellij_ue != None else intellij
    android_studio = android_studio if android_studio != None else default
    clion = clion if clion != None else default

    ide_to_value = {
        "intellij": intellij,
        "intellij-ue": intellij_ue,
        "android-studio": android_studio,
        "clion": clion,
    }

    # Map (direct ij_product) -> corresponding ide value
    params = dict()
    for ij_product, value in DIRECT_IJ_PRODUCTS.items():
        params[ij_product] = ide_to_value[value.ide]
    params["default"] = default

    return select_for_plugin_api(params)

def _plugin_api_directory(value):
    return "@" + value.directory + "//"

def select_from_plugin_api_directory(intellij, android_studio, clion, intellij_ue = None):
    """Internal convenience method to generate select statement from the IDE's plugin_api directories."""

    ide_to_value = {
        "intellij": intellij,
        "intellij-ue": intellij_ue if intellij_ue else intellij,
        "android-studio": android_studio,
        "clion": clion,
    }

    # Map (direct ij_product) -> corresponding product directory
    params = dict()
    for ij_product, value in DIRECT_IJ_PRODUCTS.items():
        params[ij_product] = [_plugin_api_directory(value) + item for item in ide_to_value[value.ide]]

    # No ij_product == intellij-latest
    params["default"] = params[INDIRECT_IJ_PRODUCTS["intellij-latest"]]

    return select_for_plugin_api(params)

def select_from_plugin_api_version_directory(params):
    """Selects for a plugin_api direct version based on its directory.

    Args:
        params: A dict with ij_product -> value.
                You may only include direct ij_products here,
                not indirects (eg. intellij-latest).
    Returns:
        A select statement on all plugin_apis. Unless you include a "default",
        a non-matched plugin_api will result in an error.
    """
    for indirect_ij_product in INDIRECT_IJ_PRODUCTS:
        if indirect_ij_product in params:
            error_message = "".join([
                "Do not select on indirect ij_product %s. " % indirect_ij_product,
                "Instead, select on an exact ij_product.",
            ])
            fail(error_message)

    # Map (direct ij_product) -> corresponding value relative to product directory
    for ij_product, value in params.items():
        if ij_product != "default":
            params[ij_product] = [_plugin_api_directory(DIRECT_IJ_PRODUCTS[ij_product]) + item for item in value]

    return _do_select_for_plugin_api(params)

def get_versions_to_build(product):
    """"Returns a set of unique product version aliases to test and build during regular release process.

    For each product, we care about four versions aliases to build and release to JetBrains
    repository; -latest, -beta, -oss-stable and oss-beta. However, some of these aliases can
    point to the same IDE version and this can lead to conflicts if we attempt to blindly
    build and upload the four versions. This function is used to return only the aliases
    that point to different IDE versions of the given product.

    Args:
        product: name of the product; android-studio, clion, intellij-ue

    Returns:
        A space separated list of product version aliases to build, the values can be
        oss-stable, oss-beta, internal-stable and internal-beta.
    """
    aliases_to_build = []
    plugin_api_versions = []
    for alias in ["oss-stable", "latest", "oss-beta", "beta"]:
        indirect_ij_product = product + "-" + alias
        if indirect_ij_product not in INDIRECT_IJ_PRODUCTS:
            fail(
                "Product-version alias %s not found." % indirect_ij_product,
                "Invalid product: %s only android-studio, clion and intellij-ue are accepted." % product,
            )

        version = INDIRECT_IJ_PRODUCTS[indirect_ij_product]
        if version not in plugin_api_versions:
            plugin_api_versions.append(version)
            if alias == "latest":
                aliases_to_build.append("internal-stable")
            elif alias == "beta":
                aliases_to_build.append("internal-beta")
            else:
                aliases_to_build.append(alias)

    return " ".join(aliases_to_build)

def combine_visibilities(*args):
    """
    Concatenates the given lists of visibilities and returns the combined list.

    If one of the given elements is //visibility:public then return //visibility:public
    If one of the lists is None, skip it.
    If the result list is empty, then return None.

    Args:
      *args: the list of visibilities lists to combine
    Returns:
      the concatenated visibility targets list
    """
    res = []
    for arg in args:
        if arg:
            for visibility in arg:
                if visibility == "//visibility:public":
                    return ["//visibility:public"]
                res.append(visibility)
    if res == []:
        return None
    return res

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
            cp "$<" "$@"
            chmod u+w "$@"
            zip -d "$@" mockito-extensions/*
            """,
        )
        output_jars.append(output_jar_name)

    native.java_import(
        name = name,
        jars = output_jars,
        **kwargs
    )
