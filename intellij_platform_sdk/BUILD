# Copyright 2011 Google Inc.  All rights reserved.
#
# Description:
#   Defines a package group that restricts access to the JetBrains
#   plugin apis to known packages that build plugins. Only packages
#   listed here may depend on these libraries.

load(
    "//:build-visibility.bzl",
    "ASPECT_TOOLS_PACKAGE",
    "INTELLIJ_PLUGINS_VISIBILITY",
    "create_test_libs_visibility_package",
)
load(
    ":build_defs.bzl",
    "select_for_plugin_api",
    "select_from_plugin_api_directory",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])  # Apache2

create_test_libs_visibility_package()

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
    name = "intellij-2021.3",
    values = {
        "define": "ij_product=intellij-2021.3",
    },
)

config_setting(
    name = "intellij-2021.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2021.3",
    },
)

config_setting(
    name = "intellij-2022.1",
    values = {
        "define": "ij_product=intellij-2022.1",
    },
)

config_setting(
    name = "intellij-2022.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2022.1",
    },
)

config_setting(
    name = "intellij-2022.2",
    values = {
        "define": "ij_product=intellij-2022.2",
    },
)

config_setting(
    name = "intellij-2022.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2022.2",
    },
)

config_setting(
    name = "intellij-2022.3",
    values = {
        "define": "ij_product=intellij-2022.3",
    },
)

config_setting(
    name = "intellij-2022.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2022.3",
    },
)

config_setting(
    name = "intellij-2023.1",
    values = {
        "define": "ij_product=intellij-2023.1",
    },
)

config_setting(
    name = "intellij-2023.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2023.1",
    },
)

config_setting(
    name = "intellij-2023.2",
    values = {
        "define": "ij_product=intellij-2023.2",
    },
)

config_setting(
    name = "intellij-2023.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2023.2",
    },
)

config_setting(
    name = "intellij-2023.3",
    values = {
        "define": "ij_product=intellij-2023.3",
    },
)

config_setting(
    name = "intellij-2023.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-2023.3",
    },
)

config_setting(
    name = "intellij-ue-2021.3",
    values = {
        "define": "ij_product=intellij-ue-2021.3",
    },
)

config_setting(
    name = "intellij-ue-2021.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2021.3",
    },
)

config_setting(
    name = "intellij-ue-2022.1",
    values = {
        "define": "ij_product=intellij-ue-2022.1",
    },
)

config_setting(
    name = "intellij-ue-2022.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2022.1",
    },
)

config_setting(
    name = "intellij-ue-2022.2",
    values = {
        "define": "ij_product=intellij-ue-2022.2",
    },
)

config_setting(
    name = "intellij-ue-2022.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2022.2",
    },
)

config_setting(
    name = "intellij-ue-2022.3",
    values = {
        "define": "ij_product=intellij-ue-2022.3",
    },
)

config_setting(
    name = "intellij-ue-2022.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2022.3",
    },
)

config_setting(
    name = "intellij-ue-2023.1",
    values = {
        "define": "ij_product=intellij-ue-2023.1",
    },
)

config_setting(
    name = "intellij-ue-2023.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2023.2",
    },
)

config_setting(
    name = "intellij-ue-2023.2",
    values = {
        "define": "ij_product=intellij-ue-2023.2",
    },
)

config_setting(
    name = "intellij-ue-2023.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2023.3",
    },
)

config_setting(
    name = "intellij-ue-2023.3",
    values = {
        "define": "ij_product=intellij-ue-2023.3",
    },
)


config_setting(
    name = "intellij-ue-2023.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=intellij-ue-2023.1",
    },
)
config_setting(
    name = "android-studio-latest",
    values = {
        "define": "ij_product=android-studio-latest",
    },
)

config_setting(
    name = "android-studio-latest-mac",
    values = {
        "cpu": "darwin_x86_64",
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
    name = "android-studio-canary-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=android-studio-canary",
    },
)

config_setting(
    name = "android-studio-oss-oldest-stable",
    values = {
        "define": "ij_product=android-studio-oss-oldest-stable",
    },
)

config_setting(
    name = "android-studio-oss-latest-stable",
    values = {
        "define": "ij_product=android-studio-oss-latest-stable",
    },
)

config_setting(
    name = "android-studio-oss-under-dev",
    values = {
        "define": "ij_product=android-studio-oss-under-dev",
    },
)

config_setting(
    name = "android-studio-2022.2",
    values = {
        "define": "ij_product=android-studio-2022.2",
    },
)

config_setting(
    name = "android-studio-2022.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=android-studio-2022.2",
    },
)

config_setting(
    name = "android-studio-2022.3",
    values = {
        "define": "ij_product=android-studio-2022.3",
    },
)

config_setting(
    name = "android-studio-2022.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=android-studio-2022.3",
    },
)

config_setting(
    name = "android-studio-2023.1",
    values = {
        "define": "ij_product=android-studio-2023.1",
    },
)

config_setting(
    name = "android-studio-2023.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=android-studio-2023.1",
    },
)

config_setting(
    name = "android-studio-2023.2",
    values = {
        "define": "ij_product=android-studio-2023.2",
    },
)

config_setting(
    name = "android-studio-2023.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=android-studio-2023.2",
    },
)

config_setting(
    name = "clion-latest",
    values = {
        "define": "ij_product=clion-latest",
    },
)

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
    name = "clion-2021.3",
    values = {
        "define": "ij_product=clion-2021.3",
    },
)

config_setting(
    name = "clion-2021.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2021.3",
    },
)

config_setting(
    name = "clion-2022.1",
    values = {
        "define": "ij_product=clion-2022.1",
    },
)

config_setting(
    name = "clion-2022.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2022.1",
    },
)

config_setting(
    name = "clion-2022.2",
    values = {
        "define": "ij_product=clion-2022.2",
    },
)

config_setting(
    name = "clion-2022.2-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2022.2",
    },
)

config_setting(
    name = "clion-2022.3",
    values = {
        "define": "ij_product=clion-2022.3",
    },
)

config_setting(
    name = "clion-2022.3-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2022.3",
    },
)

config_setting(
    name = "clion-2023.1",
    values = {
        "define": "ij_product=clion-2023.1",
    },
)

config_setting(
    name = "clion-2023.1-mac",
    values = {
        "cpu": "darwin_x86_64",
        "define": "ij_product=clion-2023.1",
    },
)

config_setting(
    name = "clion-2023.2",
    values = {
        "define": "ij_product=clion-2023.2",
    },
)

config_setting(
    name = "clion-2023.2-mac",
    values = {
        "cpu": "darwin_x86_64", #todo how about m1?
        "define": "ij_product=clion-2023.2",
    },
)

config_setting(
    name = "clion-2023.3",
    values = {
        "define": "ij_product=clion-2023.3",
    },
)

config_setting(
    name = "clion-2023.3-mac",
    values = {
        "cpu": "darwin_x86_64", #todo how about m1?
        "define": "ij_product=clion-2023.3",
    },
)


config_setting(
    name = "intellij-cc-oldest-stable",
    values = {
        "define": "ij_product=intellij-cc-oldest-stable",
    },
)

config_setting(
    name = "intellij-cc-latest-stable",
    values = {
        "define": "ij_product=intellij-cc-latest-stable",
    },
)

config_setting(
    name = "intellij-cc-under-dev",
    values = {
        "define": "ij_product=intellij-cc-under-dev",
    },
)

config_setting(
    name = "intellij-ue-cc-oldest-stable",
    values = {
        "define": "ij_product=intellij-ue-cc-oldest-stable",
    },
)

config_setting(
    name = "intellij-ue-cc-latest-stable",
    values = {
        "define": "ij_product=intellij-ue-cc-latest-stable",
    },
)

config_setting(
    name = "intellij-ue-cc-under-dev",
    values = {
        "define": "ij_product=intellij-ue-cc-under-dev",
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
            ":cidr_plugins",
            ":java",
            ":kotlin",
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
            ":kotlin",
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
            ":kotlinc_version",
        ],
        clion = [],
        # From 2022.1 forward, the version of kotlinc will be obtained
        # by KotlinPluginLayout from plugins/kotlin/kotlinc/build.txt
        # so we need to provide this to our tests
        intellij = [":kotlinc_version"],
    ),
    exports = [":plugin_api_internal"],
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
        "@bytebuddy_agent//jar",
        "@bytebuddy//jar",
        "@mockito//jar",
        "@objenesis//jar"
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
    visibility = ASPECT_TOOLS_PACKAGE,
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
        "clion-2021.3": [":bundled_jsr305"],
        "clion-2022.1": ["@jsr305_annotations//jar"],
        "clion-2022.2": ["@jsr305_annotations//jar"],
        "clion-2022.3": ["@jsr305_annotations//jar"],
        "intellij-2021.3": ["@jsr305_annotations//jar"],
        "intellij-2022.1": ["@jsr305_annotations//jar"],
        "intellij-2022.2": ["@jsr305_annotations//jar"],
        "intellij-2022.3": ["@jsr305_annotations//jar"],
        "intellij-2023.1": ["@jsr305_annotations//jar"],
        "intellij-2023.2": ["@jsr305_annotations//jar"],
        "intellij-2023.3": ["@jsr305_annotations//jar"],
        "intellij-ue-2021.3": ["@jsr305_annotations//jar"],
        "intellij-ue-2022.1": ["@jsr305_annotations//jar"],
        "intellij-ue-2022.2": ["@jsr305_annotations//jar"],
        "intellij-ue-2022.3": ["@jsr305_annotations//jar"],
        "intellij-ue-2023.1": ["@jsr305_annotations//jar"],
        "intellij-ue-2023.2": ["@jsr305_annotations//jar"],
        "intellij-ue-2023.3": ["@jsr305_annotations//jar"],
        "android-studio-2022.2": ["@jsr305_annotations//jar"],
        "android-studio-2022.3": ["@jsr305_annotations//jar"],
        "android-studio-2023.1": ["@jsr305_annotations//jar"],
        "android-studio-2023.2": ["@jsr305_annotations//jar"],
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
        android_studio = [":devkit"],
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

# IntelliJ IntelliLang plugin
java_library(
    name = "intellilang_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":intellilang"],
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

java_library(
    name = "terminal_for_tests",
    testonly = 1,
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

# IntelliJ Shared Indexes plugin
java_library(
    name = "shared_indexes",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":shared_indexes"],
        intellij_ue = [":shared_indexes"],
    ),
)

# IntelliJ Shared Indexes plugin
java_library(
    name = "shared_indexes_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":shared_indexes"],
        intellij_ue = [":shared_indexes"],
    ),
)

# IntelliJ Git4Idea plugin
java_library(
    name = "git4idea",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":git4idea"],
    ),
)

java_library(
    name = "git4idea_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":git4idea"],
    ),
)

# JetBrains com.jetbrains.codeWithMe plugin
java_library(
    name = "cwm",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":cwm"],
    ),
)

java_library(
    name = "cwm_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":cwm"],
    ),
)

# IntelliJ maven plugin
java_library(
    name = "maven",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":maven"],
    ),
)

java_library(
    name = "maven_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":maven_bundled_plugins"],
    ),
)

# IntelliJ maven model plugin
java_library(
    name = "maven_model",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":maven_model"],
    ),
)

java_library(
    name = "maven_model_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":maven_model"],
    ),
)

# IntelliJ yaml plugin
java_library(
    name = "yaml",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":yaml"],
    ),
)

java_library(
    name = "yaml_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":yaml"],
    ),
)

# IntelliJ gradle plugin
java_library(
    name = "gradle",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":gradle"],
    ),
)

java_library(
    name = "gradle_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":gradle"],
    ),
)

# IntelliJ DatabaseTools plugin
java_library(
    name = "database_tools",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [],
        intellij_ue = [":database_tools"],
    ),
)

java_library(
    name = "database_tools_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [],
        intellij_ue = [":database_tools"],
    ),
)

# IntelliJ JavaEE plugin
java_library(
    name = "javaee",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [],
        intellij_ue = [":javaee"],
    ),
)

java_library(
    name = "javaee_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [],
        intellij_ue = [":javaee"],
    ),
)

# IntelliJ Markdown plugin
java_library(
    name = "markdown",
    neverlink = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":markdown"],
    ),
)

java_library(
    name = "markdown_for_tests",
    testonly = 1,
    exports = select_from_plugin_api_directory(
        android_studio = [],
        clion = [],
        intellij = [":markdown"],
    ),
)

filegroup(
    name = "application_info_json",
    srcs = select_from_plugin_api_directory(
        android_studio = [":application_info_json"],
        clion = [":application_info_json"],
        intellij = [":application_info_json"],
    ),
)
