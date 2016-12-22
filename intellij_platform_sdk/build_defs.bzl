"""Convenience methods for plugin_api."""

# The current indirect ij_product mapping (eg. "intellij-latest")
INDIRECT_IJ_PRODUCTS = {
    "intellij-latest": "intellij-162.2032.8",
    "android-studio-latest": "android-studio-145.1617.8",
    "android-studio-beta": "android-studio-2.3.0.3",
    "clion-latest": "clion-162.1967.7",
}

DIRECT_IJ_PRODUCTS = {
    "intellij-2016.3.1": struct(
        ide="intellij",
        directory="intellij_ce_2016_3_1",
    ),
    "intellij-162.2032.8": struct(
        ide="intellij",
        directory="IC_162_2032_8",
    ),
    "android-studio-145.1617.8": struct(
        ide="android-studio",
        directory="AI_145_1617_8",
    ),
    "android-studio-2.3.0.3": struct(
        ide="android-studio",
        directory="android_studio_2_3_0_3",
    ),
    "clion-162.1628.20": struct(
        ide="clion",
        directory="CL_162_1628_20",
    ),
    "clion-162.1967.7": struct(
        ide="clion",
        directory="CL_162_1967_7",
    ),
}

# BUILD_VARS for each IDE corresponding to indirect ij_products, eg. "intellij-latest"






def select_for_plugin_api(params):
  """Selects for a plugin_api.

  Args:
      params: A dict with ij_product -> value.
              You may only include direct ij_products here,
              not indirects (eg. intellij-latest).
  Returns:
      A select statement on all plugin_apis. Unless you include a "default"
      clause any other matched plugin_api will return "None".

      A build without an ij_product is considered equivalent to building with
      "intellij-latest".

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
          "Instead, select on an exact ij_product."])
      fail(error_message)

  # To make the select work with "intellij-latest" and friends,
  # we find if the user is currently selecting on what intellij-latest
  # is resolving to, and copy that. Example:
  #
  # {"intellij-2016.3.1": "stuff"} ->
  # {"intellij-2016.3.1": "stuff", "intellij-latest": "stuff"}
  params = dict(**params)
  for indirect_ij_product, resolved_plugin_api in INDIRECT_IJ_PRODUCTS.items():
    if resolved_plugin_api in params:
      params[indirect_ij_product] = params[resolved_plugin_api]

  if "default" not in params:
    # If "intellij-latest" is supported, we set "default" to that
    # This supports building with an empty command line. Example:
    #
    # {"intellij-2016.3.1": "stuff", "intellij-latest": "stuff"} ->
    # {"intellij-2016.3.1": "stuff", "intellij-latest": "stuff", "default": "stuff"}
    if "intellij-latest" in params:
      params["default"] = params["intellij-latest"]

    # Add the other indirect ij_products returning None rather than default
    for ij_product in INDIRECT_IJ_PRODUCTS:
      if ij_product not in params:
        params[ij_product] = None
    for ij_product in DIRECT_IJ_PRODUCTS:
      if ij_product not in params:
        params[ij_product] = None

  # Map to the actual targets
  # This makes it more convenient so the user doesn't have to
  # fully specify the path to the plugin_apis
  select_params = dict()
  for ij_product, value in params.items():
    if ij_product == "default":
      select_params["//conditions:default"] = value
    else:
      select_params["//intellij_platform_sdk:" + ij_product] = value
  return select(select_params)

def select_for_ide(intellij=None, android_studio=None, clion=None, default=None):
  """Selects for the supported IDEs.

  Args:
      intellij: Files to use for IntelliJ. If None, will use default.
      android_studio: Files to use for Android Studio. If None will use default.
      clion: Files to use for CLion. If None will use default.
      default: Files to use for any IDEs not passed.
  Returns:
      A select statement on all plugin_apis, sorted into IDEs.

  Example:
    java_library(
      name = "foo",
      srcs = select_for_ide(
          clion = [":cpp_only_sources"],
          default = [":java_only_sources"],
      ),
    )
  """
  intellij = intellij or default
  android_studio = android_studio or default
  clion = clion or default
  default = default or intellij

  ide_to_value = {
      "intellij" : intellij,
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

def select_from_plugin_api_directory(intellij, android_studio, clion):
  """Internal convenience method to generate select statement from the IDE's plugin_api directories."""

  ide_to_value = {
      "intellij" : intellij,
      "android-studio": android_studio,
      "clion": clion,
  }

  # Map (direct ij_product) -> corresponding product directory
  params = dict()
  for ij_product, value in DIRECT_IJ_PRODUCTS.items():
    params[ij_product] = [_plugin_api_directory(value) + item for item in ide_to_value[value.ide]]
  return select_for_plugin_api(params)
