# Copyright 2011 Google Inc.  All rights reserved.
#
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
    name = "intellij-oss-oldest-stable",
    values = {
        "define": "ij_product=intellij-oss-oldest-stable",
    },
)

config_setting(
    name = "intellij-ue-oss-oldest-stable",
    values = {
        "define": "ij_product=intellij-ue-oss-oldest-stable",
    },
)

config_setting(
    name = "intellij-oss-latest-stable",
    values = {
        "define": "ij_product=intellij-oss-latest-stable",
    },
)

config_setting(
    name = "intellij-ue-oss-latest-stable",
    values = {
        "define": "ij_product=intellij-ue-oss-latest-stable",
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
    name = "intellij-2025.1",
    values = {"define": "ij_product=intellij-2025.1"},
)

config_setting(
    name = "intellij-2025.2",
    values = {"define": "ij_product=intellij-2025.2"},
)

config_setting(
    name = "intellij-2025.3",
    values = {"define": "ij_product=intellij-2025.3"},
)

config_setting(
    name = "intellij-2025.1-mac",
    values = {
        "define": "ij_product=intellij-2025.1",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-2025.2-mac",
    values = {
        "define": "ij_product=intellij-2025.2",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-2025.3-mac",
    values = {
        "define": "ij_product=intellij-2025.3",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-ue-2025.1",
    values = {"define": "ij_product=intellij-ue-2025.1"},
)

config_setting(
    name = "intellij-ue-2025.2",
    values = {"define": "ij_product=intellij-ue-2025.2"},
)

config_setting(
    name = "intellij-ue-2025.3",
    values = {"define": "ij_product=intellij-ue-2025.3"},
)

config_setting(
    name = "intellij-ue-2025.1-mac",
    values = {
        "define": "ij_product=intellij-ue-2025.1",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-ue-2025.2-mac",
    values = {
        "define": "ij_product=intellij-ue-2025.2",
        "cpu": "darwin_x86_64",
    },
)

config_setting(
    name = "intellij-ue-2025.3-mac",
    values = {
        "define": "ij_product=intellij-ue-2025.3",
        "cpu": "darwin_x86_64",
    },
)

# The purpose of this rule is to hide the versioning
# complexity from users of this api.
# There will be additional versions added in the future
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [
            ":sdk",
            ":java",
            ":kotlin",
            ":platform_images",
        ],
    ),
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
    data = select_from_plugin_api_directory(
        # Provide access to clion bundled tools to tests (like debugers).
        # From 2022.1 forward, the version of kotlinc will be obtained
        # by KotlinPluginLayout from plugins/kotlin/kotlinc/build.txt
        # so we need to provide this to our tests
        intellij = [":kotlinc_version"],
    ),
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
    exports = select_from_plugin_api_directory(
        intellij = [":guava"],
    ),
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
    exports = select_from_plugin_api_directory(
        intellij = [":hg4idea"],
    ),
    name = "hg4idea",
    neverlink = 1,
)

# IntelliJ Kotlin plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":kotlin"],
    ),
    name = "kotlin",
    neverlink = 1,
)

# IntelliJ Kotlin plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":kotlin"],
    ),
    name = "kotlin_for_tests",
    testonly = 1,
)

# IntelliJ JUnit plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":junit"],
    ),
    name = "junit",
    neverlink = 1,
)

# terminal plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":terminal"],
    ),
    name = "terminal",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":terminal"],
    ),
    name = "terminal_for_tests",
    testonly = 1,
)

# Bundled plugins required by integration tests
java_library(
    name = "bundled_plugins",
    runtime_deps = select_from_plugin_api_directory(
        intellij = [":bundled_plugins"],
    ),
    testonly = 1,
)

# IntelliJ Shared Indexes plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":shared_indexes"],
        intellij_ue = [":shared_indexes"],
    ),
    name = "shared_indexes",
    neverlink = 1,
)

# IntelliJ Shared Indexes plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":shared_indexes"],
        intellij_ue = [":shared_indexes"],
    ),
    name = "shared_indexes_for_tests",
    testonly = 1,
)

# IntelliJ Git4Idea plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":git4idea"],
    ),
    name = "git4idea",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":git4idea"],
    ),
    name = "git4idea_for_tests",
    testonly = 1,
)

# JetBrains com.jetbrains.codeWithMe plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":cwm"],
    ),
    name = "cwm",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":cwm"],
    ),
    name = "cwm_for_tests",
    testonly = 1,
)

# IntelliJ maven plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":maven"],
    ),
    name = "maven",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":maven_bundled_plugins"],
    ),
    name = "maven_for_tests",
    testonly = 1,
)

# IntelliJ maven model plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":maven_model"],
    ),
    name = "maven_model",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":maven_model"],
    ),
    name = "maven_model_for_tests",
    testonly = 1,
)

# IntelliJ yaml plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":yaml"],
    ),
    name = "yaml",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":yaml"],
    ),
    name = "yaml_for_tests",
    testonly = 1,
)

# IntelliJ gradle plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":gradle"],
    ),
    name = "gradle",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":gradle"],
    ),
    name = "gradle_for_tests",
    testonly = 1,
)

# IntelliJ DatabaseTools plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [],
        intellij_ue = [":database_tools"],
    ),
    name = "database_tools",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [],
        intellij_ue = [":database_tools"],
    ),
    name = "database_tools_for_tests",
    testonly = 1,
)

# IntelliJ JavaEE plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [],
        intellij_ue = [":javaee"],
    ),
    name = "javaee",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [],
        intellij_ue = [":javaee"],
    ),
    name = "javaee_for_tests",
    testonly = 1,
)

# IntelliJ Markdown plugin
java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":markdown"],
    ),
    name = "markdown",
    neverlink = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":markdown"],
    ),
    name = "markdown_for_tests",
    testonly = 1,
)

java_library(
    exports = select_from_plugin_api_directory(
        intellij = [":json"],
    ),
    name = "json_for_tests",
    testonly = 1,
)

filegroup(
    name = "application_info_json",
    srcs = select_from_plugin_api_directory(
        intellij = [":application_info_json"],
    ),
)

write_file(
    content = select_build_name(),
    name = "build_name",
    out = "build_name.txt",
)
