# Copyright 2011 Google Inc.  All rights reserved.
#
# Description:
#   Defines a package group that restricts access to the JetBrains
#   plugin apis to known packages that build plugins. Only packages
#   listed here may depend on these libraries.

licenses(["notice"])  # Apache2

package(default_visibility = ["//visibility:public"])

config_setting(
    name = "intellij-latest",
    values = {
        "define": "ij_product=intellij-latest",
    },
)

# IntelliJ CE 2016.3.1
config_setting(
    name = "intellij-2016.3.1",
    values = {
        "define": "ij_product=intellij-2016.3.1",
    },
)

# IntelliJ CE 2016.2.4
config_setting(
    name = "intellij-162.2032.8",
    values = {
        "define": "ij_product=intellij-162.2032.8",
    },
)

config_setting(
    name = "android-studio-latest",
    values = {
        "define": "ij_product=android-studio-latest",
    },
)

# Android Studio 2.2.0.7
config_setting(
    name = "android-studio-145.1617.8",
    values = {
        "define": "ij_product=android-studio-145.1617.8",
    },
)

config_setting(
    name = "android-studio-beta",
    values = {
        "define": "ij_product=android-studio-beta",
    },
)

# Android Studio 2.3.0.3
config_setting(
    name = "android-studio-2.3.0.3",
    values = {
        "define": "ij_product=android-studio-2.3.0.3",
    },
)

# Android Studio 2.3.0.4
config_setting(
    name = "android-studio-2.3.0.4",
    values = {
        "define": "ij_product=android-studio-2.3.0.4",
    },
)

config_setting(
    name = "clion-latest",
    values = {
        "define": "ij_product=clion-latest",
    },
)

# CLion 2016.3.2
config_setting(
    name = "clion-2016.3.2",
    values = {
        "define": "ij_product=clion-2016.3.2",
    },
)

# CLion 2016.2.2
config_setting(
    name = "clion-162.1967.7",
    values = {
        "define": "ij_product=clion-162.1967.7",
    },
)

load(
    ":build_defs.bzl",
    "select_for_ide",
    "select_for_plugin_api",
    "select_from_plugin_api_directory",
)

# The purpose of this rule is to hide the versioning
# complexity from users of this api.
# There will be additional versions added in the future
java_library(
    name = "plugin_api_internal",
    exports = select_from_plugin_api_directory(
        android_studio = [
            ":sdk",
            ":android_plugin",
            ":test_recorder",
        ],
        clion = [":sdk"],
        intellij = [":sdk"],
    ),
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

# For the grammar-kit binary only (a build time tool). It needs the IJ API for runtime.
# The clion.jar doesn't work (missing MockEditorFactory, MockProjectEx), so just use
# an idea.jar. It doesn't affect the generated code.
java_library(
    name = "plugin_api_for_grammar_kit",
    visibility = ["//third_party/java/jetbrains/grammar_kit:__pkg__"],
    exports = ["//intellij_platform_sdk/intellij_ce_2016_3_1:sdk"],
)

# Used to support IntelliJ plugin development in our plugin
java_library(
    name = "devkit",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":devkit"],
    ),
)

# IntelliJ Mercurial plugin
java_library(
    name = "hg4idea",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":hg4idea"],
        clion = [":hg4idea"],
        intellij = [":hg4idea"],
    ),
)

# IntelliJ JUnit plugin
java_library(
    name = "junit",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":junit"],
        clion = [],
        intellij = [":junit"],
    ),
)

# Bundled plugins required by integration tests
java_library(
    name = "bundled_plugins",
    testonly = 1,
    runtime_deps = select_from_plugin_api_directory(
        android_studio = [":bundled_plugins"],
        clion = [":bundled_plugins"],
        intellij = [":bundled_plugins"],
    ) + [":missing_test_classes"],
)

# Certain plugin API releases don't contain test classes, so any testServiceImplemention
# in an upstream plugin will cause an error when the plugin is loaded for integration tests.
# Here we have dummy versions of these missing classes.
java_library(
    name = "missing_test_classes",
    srcs = select_for_plugin_api({
        "android-studio-2.3.0.3": [
            "missing/tests/com/jetbrains/cidr/modulemap/resolve/MockModuleMapManagerImpl.java",
        ],
        "android-studio-2.3.0.4": [
            "missing/tests/com/jetbrains/cidr/modulemap/resolve/MockModuleMapManagerImpl.java",
        ],
        "default": [],
    }) + ["missing/src/dummy/pkg/DummyClassToAvoidAnEmptyJavaLibrary.java"],
    tags = ["intellij-missing-test-classes"],
    deps = [":plugin_api"],
)

filegroup(
    name = "application_info_jar",
    srcs = select_from_plugin_api_directory(
        android_studio = [":application_info_jar"],
        clion = [":application_info_jar"],
        intellij = [":application_info_jar"],
    ),
)

filegroup(
    name = "application_info_name",
    srcs = select_for_ide(
        android_studio = ["android_studio_application_info_name.txt"],
        clion = ["clion_application_info_name.txt"],
        default = ["intellij_application_info_name.txt"],
        intellij = ["intellij_application_info_name.txt"],
    ),
)
