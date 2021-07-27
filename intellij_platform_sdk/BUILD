# Copyright 2011 Google Inc.  All rights reserved.
#
# Description:
#   Defines a package group that restricts access to the JetBrains
#   plugin apis to known packages that build plugins. Only packages
#   listed here may depend on these libraries.

load(
    ":build_defs.bzl",
    "select_for_ide",
    "select_for_plugin_api",
    "select_from_plugin_api_directory",
    "select_from_plugin_api_version_directory",
)

licenses(["notice"])  # Apache2

package(default_visibility = ["//visibility:public"])

config_setting(
    name = "intellij-latest",
    values = {
        "define": "ij_product=intellij-latest",
    },
)

config_setting(
    name = "intellij-latest-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-latest",
    },
)

config_setting(
    name = "intellij-ue-latest",
    values = {
        "define": "ij_product=intellij-ue-latest",
    },
)

config_setting(
    name = "intellij-ue-latest-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-latest",
    },
)

config_setting(
    name = "intellij-beta",
    values = {
        "define": "ij_product=intellij-beta",
    },
)

config_setting(
    name = "intellij-ue-beta",
    values = {
        "define": "ij_product=intellij-ue-beta",
    },
)

config_setting(
    name = "intellij-oss-stable",
    values = {
        "define": "ij_product=intellij-oss-stable",
    },
)

config_setting(
    name = "intellij-ue-oss-stable",
    values = {
        "define": "ij_product=intellij-ue-oss-stable",
    },
)

config_setting(
    name = "intellij-oss-beta",
    values = {
        "define": "ij_product=intellij-oss-beta",
    },
)

config_setting(
    name = "intellij-ue-oss-beta",
    values = {
        "define": "ij_product=intellij-ue-oss-beta",
    },
)

config_setting(
    name = "intellij-oss-under-dev",
    values = {
        "define": "ij_product=intellij-oss-under-dev",
    },
)

config_setting(
    name = "intellij-ue-oss-under-dev",
    values = {
        "define": "ij_product=intellij-ue-oss-under-dev",
    },
)

config_setting(
    name = "intellij-under-dev",
    values = {
        "define": "ij_product=intellij-under-dev",
    },
)

config_setting(
    name = "intellij-ue-under-dev",
    values = {
        "define": "ij_product=intellij-ue-under-dev",
    },
)

config_setting(
    name = "intellij-2020.2",
    values = {
        "define": "ij_product=intellij-2020.2",
    },
)

config_setting(
    name = "intellij-2020.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2020.2",
    },
)

config_setting(
    name = "intellij-2021.1",
    values = {
        "define": "ij_product=intellij-2021.1",
    },
)

config_setting(
    name = "intellij-2021.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2021.1",
    },
)

config_setting(
    name = "intellij-2021.2",
    values = {
        "define": "ij_product=intellij-2021.2",
    },
)

config_setting(
    name = "intellij-2021.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2021.2",
    },
)

config_setting(
    name = "intellij-2020.3",
    values = {
        "define": "ij_product=intellij-2020.3",
    },
)

config_setting(
    name = "intellij-2020.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2020.3",
    },
)

config_setting(
    name = "intellij-ue-2020.2",
    values = {
        "define": "ij_product=intellij-ue-2020.2",
    },
)

config_setting(
    name = "intellij-ue-2020.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2020.2",
    },
)

config_setting(
    name = "intellij-ue-2020.3",
    values = {
        "define": "ij_product=intellij-ue-2020.3",
    },
)

config_setting(
    name = "intellij-ue-2020.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2020.3",
    },
)

config_setting(
    name = "intellij-ue-2021.1",
    values = {
        "define": "ij_product=intellij-ue-2021.1",
    },
)

config_setting(
    name = "intellij-ue-2021.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2021.1",
    },
)

config_setting(
    name = "intellij-ue-2021.2",
    values = {
        "define": "ij_product=intellij-ue-2021.2",
    },
)

config_setting(
    name = "intellij-ue-2021.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2021.2",
    },
)

config_setting(
    name = "android-studio-latest",
    values = {
        "define": "ij_product=android-studio-latest",
    },
)

config_setting(
    name = "android-studio-beta",
    values = {
        "define": "ij_product=android-studio-beta",
    },
)

config_setting(
    name = "android-studio-beta-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=android-studio-beta",
    },
)

config_setting(
    name = "android-studio-canary",
    values = {
        "define": "ij_product=android-studio-canary",
    },
)

config_setting(
    name = "android-studio-oss-stable",
    values = {
        "define": "ij_product=android-studio-oss-stable",
    },
)

config_setting(
    name = "android-studio-oss-beta",
    values = {
        "define": "ij_product=android-studio-oss-beta",
    },
)

config_setting(
    name = "android-studio-oss-under-dev",
    values = {
        "define": "ij_product=android-studio-oss-under-dev",
    },
)

config_setting(
    name = "android-studio-4.2",
    values = {
        "define": "ij_product=android-studio-4.2",
    },
)

config_setting(
    name = "android-studio-4.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=android-studio-4.2",
    },
)

config_setting(
    name = "android-studio-2020.3",
    values = {
        "define": "ij_product=android-studio-2020.3",
    },
)

config_setting(
    name = "clion-latest",
    values = {
        "define": "ij_product=clion-latest",
    },
)

config_setting(
    name = "clion-oss-stable",
    values = {
        "define": "ij_product=clion-oss-stable",
    },
)

config_setting(
    name = "clion-oss-beta",
    values = {
        "define": "ij_product=clion-oss-beta",
    },
)

config_setting(
    name = "clion-oss-under-dev",
    values = {
        "define": "ij_product=clion-oss-under-dev",
    },
)

config_setting(
    name = "clion-latest-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-latest",
    },
)

config_setting(
    name = "clion-beta",
    values = {
        "define": "ij_product=clion-beta",
    },
)

config_setting(
    name = "clion-under-dev",
    values = {
        "define": "ij_product=clion-under-dev",
    },
)

config_setting(
    name = "clion-2020.2",
    values = {
        "define": "ij_product=clion-2020.2",
    },
)

config_setting(
    name = "clion-2020.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2020.2",
    },
)

config_setting(
    name = "clion-2020.3",
    values = {
        "define": "ij_product=clion-2020.3",
    },
)

config_setting(
    name = "clion-2020.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2020.3",
    },
)

config_setting(
    name = "clion-2021.1",
    values = {
        "define": "ij_product=clion-2021.1",
    },
)

config_setting(
    name = "clion-2021.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2021.1",
    },
)

# The purpose of this rule is to hide the versioning
# complexity from users of this api.
# There will be additional versions added in the future
java_library(
    name = "plugin_api_internal",
    visibility = ["//visibility:private"],
    exports = select_from_plugin_api_directory(
        android_studio = [
            ":sdk",
            ":java",
            ":android",
            ":test_recorder",
            ":platform_images",
        ],
        clion = [
            ":sdk",
            ":clion_plugins",
        ],
        intellij = [
            ":sdk",
            ":java",
            ":platform_images",
        ],
    ),
)

# The outward facing plugin api
java_library(
    name = "plugin_api",
    neverlink = 1,
    exports = [":plugin_api_internal"],
)

# For tests, we need the IJ API at runtime, so we can't use the neverlink rule.
java_library(
    name = "plugin_api_for_tests",
    testonly = 1,
    data = select_from_plugin_api_directory(
        android_studio = [
            ":layoutlib_data",
        ],
        clion = [],
        intellij = [],
    ),
    exports = [":plugin_api_internal"],
)

package_group(
    name = "test_libs_visibility",
    packages = [
        "//...",
        "//java/com/google/devtools/intellij/blaze/plugin/...",
        "//javatests/com/google/assistant/galup/devtools/intellij/...",
        "//javatests/com/google/dart/intellij/...",
        "//javatests/com/google/devtools/intellij/blaze/plugin/...",
        "//javatests/com/google/firebase/rules/plugins/intellij/...",
        "//third_party/java/jetbrains/dart/...",
        "//third_party/java_src/intellijprotobufplugin/...",
        "//third_party/java_src/pytype_pycharm/...",
    ],
)

# These libraries should only be used by open-source plugins.
# Otherwise, prefer using the google3 versions of these libraries. Note that
# //third_party/java/truth must be repackaged, because it pulls in Guava.
java_library(
    name = "test_libs",
    testonly = 1,
    visibility = [":test_libs_visibility"],
    exports = [
        "//third_party:truth",
        "@bytebuddy-agent//jar",
        "@bytebuddy//jar",
        "@mockito//jar",
    ],
)

# The version of guava bundled with the IntelliJ plugin API.
java_library(
    name = "guava",
    testonly = True,
    exports = select_from_plugin_api_directory(
        android_studio = [":guava"],
        clion = [":guava"],
        intellij = [":guava"],
    ),
)

java_library(
    name = "guava_for_external_binaries",
    visibility = ["//aspect/tools:__pkg__"],
    exports = select_from_plugin_api_directory(
        android_studio = [":guava"],
        clion = [":guava"],
        intellij = [":guava"],
    ),
)

# Some versions of the IDE include jsr305.jar, but others don't. So we need to include the JSR 305
# classes (@Nullable, etc.) in the plugin jars for IDEs that don't already have it on the classpath.
java_library(
    name = "jsr305",
    exports = select_for_plugin_api({
        "clion-2020.2": [":bundled_jsr305"],
        "clion-2020.3": [":bundled_jsr305"],
        "clion-2021.1": [":bundled_jsr305"],
        "intellij-2020.2": [":bundled_jsr305"],
        "intellij-2020.3": [":bundled_jsr305"],
        "intellij-2021.1": [":bundled_jsr305"],
        "intellij-2021.2": ["@jsr305_annotations//jar"],
        "intellij-ue-2020.2": ["@jsr305_annotations//jar"],
        "intellij-ue-2020.3": [":bundled_jsr305"],
        "intellij-ue-2021.1": ["@jsr305_annotations//jar"],
        "intellij-ue-2021.2": ["@jsr305_annotations//jar"],
        "android-studio-4.2": ["@jsr305_annotations//jar"],
        "android-studio-2020.3": ["@jsr305_annotations//jar"],
        # We default to intellij-latest when no plugin api is selected.
        "default": [":bundled_jsr305"],
    }),
)

# The version of jsr305 bundled with the IntelliJ plugin API.
java_library(
    name = "bundled_jsr305",
    neverlink = 1,
    visibility = ["//visibility:private"],
    exports = select_from_plugin_api_directory(
        android_studio = [":jsr305"],
        clion = [":jsr305"],
        intellij = [":jsr305"],
    ),
)

# The version of truth bundled with the IntelliJ plugin API.
java_library(
    name = "truth",
    testonly = 1,
    exports = ["//third_party:truth"],
)

# IntelliJ Coverage plugin
java_library(
    name = "coverage",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":coverage"],
        clion = [],
        intellij = [":coverage"],
    ),
)

# IntelliJ Coverage plugin
java_library(
    name = "coverage_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":coverage"],
        clion = [],
        intellij = [":coverage"],
    ),
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

# IntelliJ Kotlin plugin
java_library(
    name = "kotlin",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":kotlin"],
        clion = [],
        intellij = [":kotlin"],
    ),
)

# IntelliJ Kotlin plugin
java_library(
    name = "kotlin_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":kotlin"],
        clion = [],
        intellij = [":kotlin"],
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

# IntelliJ tasks plugin
java_library(
    name = "tasks",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":tasks"],
        clion = [":tasks"],
        intellij = [":tasks"],
    ),
)

# terminal plugin
java_library(
    name = "terminal",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [":terminal"],
        clion = [":terminal"],
        intellij = [":terminal"],
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
    ),
)

# Not all plugin api versions have lib/resources.jar from which we used to get
# the application information to set the idea-version compatibility range of the plugin.
# However, product-info.json contains this information but it was not imported to google3
# with previous plugin api versions. So the application_info_container will contain
# the application_info_jar for previous versions and application_info_json for new ones.
filegroup(
    name = "application_info_container",
    srcs = select_from_plugin_api_version_directory({
        "intellij-2021.2": [":application_info_json"],
        "intellij-ue-2021.2": [":application_info_json"],
        "default": [":application_info_jar"],
    }),
)

# #api211: product-info.json will be imported to google3 from 2021.2 on. Therefore after removing
# 2021.1 support there will be no need for application_info_jar and application_info_container
# and we can update api_version_txt script to depend only on application_info_json.
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
        intellij_ue = ["intellij_ue_application_info_name.txt"],
    ),
)
