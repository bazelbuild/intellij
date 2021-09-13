"""Lists of package_groups to be used externally as visibilities in BUILD files targets."""

# Visibility labels lists
PLUGIN_PACKAGES_VISIBILITY = ["//visibility:public"]
G3PLUGINS_VISIBILITY = None  # this is similar to not having visibility attribute
INTELLIJ_PLUGINS_VISIBILITY = ["//visibility:public"]

TEST_ASWB_SUBPACKAGES_VISIBILITY = None
ASWB_SUBPACKAGES_VISIBILITY = None
ASWB_PACKAGES_VISIBILITY = None
ASWB_PLUGIN_PACKAGES_VISIBILITY = None

CLWB_PACKAGES_VISIBILITY = None

IJWB_PACKAGES_VISIBILIY = None

GOLANG_PACKAGES_VISIBILITY = None

ASPECT_PROTO_VISIBILITY = None

BAZEL_PLUGIN_SUBPACKAGES = ["//:__subpackages__"]

JAVASCRIPT_PACKAGES_VISIBILITY = None

PYTHON_PACKAGES_VISIBILITY = None

SKYLARK_PACKAGES_VISIBILITY = None

FAST_BUILD_JAVAC_VISIBILITY = [
    "//aswb:__pkg__",
    "//ijwb:__pkg__",
]

def create_plugin_packages_group(name = None):
    # This group is not needed externally
    pass

def create_proto_visibility_group(name = None):
    # This group is not needed externally
    pass
