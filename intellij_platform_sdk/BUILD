# Description:
#   Defines a package group that restricts access to the JetBrains
#   plugin apis to known packages that build plugins. Only packages
#   listed here may depend on these libraries.

load("@bazel_skylib//rules:write_file.bzl", "write_file")
load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
    "create_test_libs_visibility_package",
)
load(
    ":build_defs.bzl",
    "select_build_name",
    "select_from_plugin_api_directory",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

create_test_libs_visibility_package()

config_setting(
    name = "clion-oss-oldest-stable",
    values = {
        "define": "ij_product=clion-oss-oldest-stable",
    },
)

config_setting(
    name = "clion-oss-latest-stable",
    values = {
        "define": "ij_product=clion-oss-latest-stable",
    },
)

config_setting(
    name = "clion-oss-under-dev",
    values = {
        "define": "ij_product=clion-oss-under-dev",
    },
)

config_setting(
    name = "clion-2025.1",
    values = {"define": "ij_product=clion-2025.1"},
)

config_setting(
    name = "clion-2025.2",
    values = {"define": "ij_product=clion-2025.2"},
)

config_setting(
    name = "clion-2025.3",
    values = {"define": "ij_product=clion-2025.3"},
)

config_setting(
    name = "clion-2025.1-mac",
    values = {
        "define": "ij_product=clion-2025.1",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "clion-2025.2-mac",
    values = {
        "define": "ij_product=clion-2025.2",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "clion-2025.3-mac",
    values = {
        "define": "ij_product=clion-2025.3",
        "cpu": "darwin_x86_64",
    },
)

# The purpose of this rule is to hide the versioning
# complexity from users of this api.
# There will be additional versions added in the future
java_library(
    exports = select_from_plugin_api_directory([
        ":clion_plugins",
        ":sdk",
    ]),
    name = "plugin_api_internal",
    visibility = ["//visibility:private"],
)

# The outward facing plugin api
java_library(
    exports = [":plugin_api_internal"],
    name = "plugin_api",
    neverlink = 1,
)

# For tests, we need the IJ API at runtime, so we can't use the neverlink rule.
java_library(
    data = select_from_plugin_api_directory([":clion_tools"]),
    exports = [":plugin_api_internal"],
    name = "plugin_api_for_tests",
    testonly = 1,
)

# These libraries should only be used by open-source plugins.
# Otherwise, prefer using the google3 versions of these libraries. Note that
# //third_party/java/truth must be repackaged, because it pulls in Guava.
java_library(
    exports = [
        "//third_party/java/truth",
        "@bytebuddy//jar",
        "@bytebuddy_agent//jar",
        "@mockito//jar",
        "@objenesis//jar",
    ],
    name = "test_libs",
    testonly = 1,
    visibility = [":test_libs_visibility"],
)

# The version of guava bundled with the IntelliJ plugin API.
java_library(
    exports = select_from_plugin_api_directory([":guava"]),
    name = "guava",
)

java_library(
    exports = ["@jsr305_annotations//jar"],
    name = "jsr305",
)

# The version of truth bundled with the IntelliJ plugin API.
java_library(
    exports = [
        "//third_party/java/truth",
        "@diffutils//jar",
    ],
    name = "truth",
    testonly = 1,
)

# IntelliJ Mercurial plugin
java_library(
    exports = select_from_plugin_api_directory([":hg4idea"]),
    name = "hg4idea",
    neverlink = 1,
)

# terminal plugin
java_library(
    exports = select_from_plugin_api_directory([":terminal"]),
    name = "terminal",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory([":terminal"]),
    name = "terminal_for_tests",
    testonly = 1,
)

# Bundled plugins required by integration tests
java_library(
    name = "bundled_plugins",
    runtime_deps = select_from_plugin_api_directory([":bundled_plugins"]),
    testonly = 1,
)

filegroup(
    name = "application_info_json",
    srcs = select_from_plugin_api_directory([":application_info_json"]),
)

write_file(
    content = select_build_name(),
    name = "build_name",
    out = "build_name.txt",
)
