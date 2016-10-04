#
# Description: IntelliJ plugin SDKs required to build the plugin jars.
#

package(default_visibility = ["//visibility:public"])

config_setting(
    name = "intellij-latest",
    values = {
        "define": "ij_product=intellij-latest",
    },
)

config_setting(
    name = "clion-latest",
    values = {
        "define": "ij_product=clion-latest",
    },
)

config_setting(
    name = "android-studio-latest",
    values = {
        "define": "ij_product=android-studio-latest",
    },
)

java_library(
    name = "plugin_api_internal",
    visibility = ["//visibility:private"],
    exports = select({
        ":intellij-latest": ["@intellij_latest//:plugin_api"],
        ":clion-latest": ["@clion_latest//:plugin_api"],
        ":android-studio-latest": [
            "@android_studio_latest//:plugin_api",
            "@android_studio_latest//:android_plugin",
        ],
        "//conditions:default": ["@intellij_latest//:plugin_api"],
    }),
)

# The outward facing plugin api
java_library(
    name = "plugin_api",
    neverlink = 1,
    exports = [":plugin_api_internal"],
)

# for tests, we need the IJ API at runtime,
# so can't use the neverlink rule
java_library(
    name = "plugin_api_for_tests",
    testonly = 1,
    exports = [
        ":plugin_api_internal",
        "@mockito//jar",
        "@objenesis//jar",
        "@truth//jar",
    ],
)

# The dev kit is only for IntelliJ since you only develop plugins in Java.
java_library(
    name = "devkit",
    neverlink = 1,
    exports = select({
        ":intellij-latest": ["@intellij_latest//:devkit"],
        ":android-studio-latest": [],
        ":clion-latest": [],
        "//conditions:default": ["@intellij_latest//:devkit"],
    }),
)

# Bundled plugins required by integration tests
java_library(
    name = "bundled_plugins",
    testonly = 1,
    runtime_deps = select({
        ":intellij-latest": ["@intellij_latest//:bundled_plugins"],
        ":clion-latest": ["@clion_latest//:bundled_plugins"],
        ":android-studio-latest": ["@android_studio_latest//:bundled_plugins"],
        "//conditions:default": ["@intellij_latest//:bundled_plugins"],
    }),
)

filegroup(
    name = "application_info_jar",
    srcs = select({
        ":intellij-latest": ["@intellij_latest//:application_info_jar"],
        ":clion-latest": ["@clion_latest//:application_info_jar"],
        ":android-studio-latest": ["@android_studio_latest//:application_info_jar"],
        "//conditions:default": ["@intellij_latest//:application_info_jar"],
    }),
)

filegroup(
    name = "application_info_name",
    srcs = select({
        ":intellij-latest": ["intellij_application_info_name.txt"],
        ":clion-latest": ["clion_application_info_name.txt"],
        ":android-studio-latest": ["android_studio_application_info_name.txt"],
        "//conditions:default": ["intellij_application_info_name.txt"],
    }),
)
