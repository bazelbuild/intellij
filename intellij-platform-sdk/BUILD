#
# Description: IntelliJ plugin SDKs required to build the plugin jars.
#

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
    exports = select({
        ":intellij-latest": ["@intellij_latest//:plugin_api"],
        ":clion-latest": ["@clion_latest//:plugin_api"],
        ":android-studio-latest": ["//intellij-platform-sdk/AI-145.971.21:plugin_api"],
        "//conditions:default": ["@intellij_latest//:plugin_api"],
    }),
)

# The outward facing plugin api
java_library(
    name = "plugin_api",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = [":plugin_api_internal"],
)

# for tests, we need the IJ API at runtime,
# so can't use the neverlink rule
java_library(
    name = "plugin_api_for_tests",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [":plugin_api_internal"],
)

# The dev kit is only for IntelliJ since you only develop plugins in Java.
java_library(
    name = "devkit",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = select({
        ":intellij-latest": ["@intellij_latest//:devkit"],
        "//conditions:default": ["@intellij_latest//:devkit"],
    }),
)

filegroup(
    name = "build_number",
    srcs = select({
        ":intellij-latest": ["@intellij_latest//:build_number"],
        ":clion-latest": ["@clion_latest//:build_number"],
        ":android-studio-latest": ["//intellij-platform-sdk/AI-145.971.21:build_number"],
        "//conditions:default": ["@intellij_latest//:build_number"],
    }),
    visibility = ["//visibility:public"],
)

# Plugins bundled with the SDK which are required for compilation and/or integration tests
java_library(
    name = "bundled_plugins_internal",
    exports = select({
        ":intellij-latest": ["@intellij_latest//:bundled_plugins"],
        ":clion-latest": ["@clion_latest//:bundled_plugins"],
        ":android-studio-latest": ["//intellij-platform-sdk/AI-145.971.21:bundled_plugins"],
        "//conditions:default": ["@intellij_latest//:bundled_plugins"],
    }),
)

java_library(
    name = "bundled_plugins",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = [":bundled_plugins_internal"],
)

# for tests, we include the bundled plugins at runtime,
# so can't use the neverlink rule
java_library(
    name = "bundled_plugins_for_tests",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [":bundled_plugins_internal"],
)
