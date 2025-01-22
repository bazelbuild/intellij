load("//intellij_platform_sdk:build_defs.bzl", "INDIRECT_IJ_PRODUCTS")

SUPPORTED_VERSIONS = {
    "clion-oss-oldest-stable": INDIRECT_IJ_PRODUCTS["clion-oss-oldest-stable"],
    "clion-oss-latest-stable": INDIRECT_IJ_PRODUCTS["clion-oss-latest-stable"],
    "clion-oss-under-dev": INDIRECT_IJ_PRODUCTS["clion-oss-under-dev"],
}

def _version_to_number(version):
    """
    Turns a clion version `clion-20xx.x` to an integer. Used for comparing clion versions.
    """

    # take the last six characters of the version `20xx.x `
    version = version[-6:]

    # replace the dot with a 0
    version = version.replace(".", "0")

    return int(version)

def select_since(since, value, default = None):
    """
    Returns a select that on targeted clion version. The select returns the `value` if the target version is bigger or
    equal to the specified `version`. If a default value is defined this value is returned otherwise.

    Might be a good replacement for sdkcompat if only future versions are targeted.

    Args:
      since: the minimum supported version
      value: the value to select if the current target version is bigger or equal than `since`
      default: a optional default value
    """

    select_params = dict()

    for name, version in SUPPORTED_VERSIONS.items():
        if _version_to_number(version) >= _version_to_number(since):
            select_params["//intellij_platform_sdk:" + version] = value
            select_params["//intellij_platform_sdk:" + name] = value

    if default != None:
        select_params["//conditions:default"] = default

    return select(
        select_params,
        no_match_error = "unsupported clion version, min version: clion-" + since,
    )
