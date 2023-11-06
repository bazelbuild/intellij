"""
This module contains the rules that exposes a plugin api for android studio.
"""

load("//intellij_platform_sdk:build_defs.bzl", "no_mockito_extensions")

def _glob(files, dir, extension, recursive = False, exclude = []):
    if files:
        fail("Expected no files in _glob")
    rec = "/**" if recursive else ""
    return native.glob(
        include = ["android-studio/" + dir + rec + "/*" + extension],
        exclude = ["android-studio/" + f for f in exclude],
    )

def _file_list(files, dir, extension, recursive = False, exclude = []):
    ret = []
    for file in files:
        if file in exclude:
            continue

        file_dir, _, file_name = file.rpartition("/")
        in_dir = file_dir == dir
        if recursive:
            in_dir = in_dir or file_dir.startswith(dir + "/")

        if in_dir and file_name.endswith(extension):
            ret.append("android-studio/" + file)
    return ret

def android_studio(name, tar = None, files = None):
    """
    Macro that creates the rules to depend on for android studio plugin development

    If tar and files are given, the files are assumed to be compressed in a tar,
    with files being the list of files.

    If not, android studio is assumed to be in the "android-studio" subdirectory.

    Args:
      name: Currently unused, for backwards compatibility all rules are named top-level
      tar: the tar that contains android studio.
      files: the list of files inside the tar.
    """
    if not tar or not files:
        if tar or files:
            fail("Specify both tar and files or none of them")
        _android_studio(name, files, _glob)
    else:
        all_files = _android_studio(name, files, _file_list)

        native.genrule(
            name = name,
            srcs = [tar],
            outs = all_files,
            cmd = "tar -xzvf $< -C $(RULEDIR) 1>/dev/null 2>&1 " + " ".join(all_files),
        )

def _android_studio(name, files, my_glob):
    unpacked = []

    # TODO: All these targets should be prefixed with ${name}, but for now keeping backwards compatibility.
    kotlin_jars = my_glob(files, "plugins/Kotlin/lib", ".jar")
    native.java_import(
        name = "kotlin",
        jars = kotlin_jars,
        tags = ["incomplete-deps"],
        visibility = ["//visibility:public"],
    )
    unpacked += kotlin_jars

    terminal_jars = ["android-studio/plugins/terminal/lib/terminal.jar"]
    native.java_import(
        name = "terminal",
        jars = terminal_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += terminal_jars

    java_jars = my_glob(files, "plugins/java/lib", ".jar") + ["android-studio/plugins/java/lib/resources/jdkAnnotations.jar"]
    native.java_import(
        name = "java",
        jars = java_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += java_jars

    platform_images_jars = my_glob(files, "plugins/platform-images/lib", ".jar")
    native.java_import(
        name = "platform_images",
        jars = platform_images_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += platform_images_jars

    devkit_jars = my_glob(files, "plugins/devkit/lib", ".jar")
    native.java_import(
        name = "devkit",
        jars = devkit_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += devkit_jars

    hg4idea_jars = my_glob(files, "plugins/vcs-hg/lib", ".jar")
    native.java_import(
        name = "hg4idea",
        jars = hg4idea_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += hg4idea_jars

    android_jars = (
        my_glob(files, "plugins/android/lib", ".jar") +
        my_glob(files, "plugins/studio-bot/lib", ".jar") +
        my_glob(files, "plugins/android-layoutlib/lib", ".jar") +
        my_glob(files, "plugins/android-wizardTemplate-plugin/lib", ".jar") +
        my_glob(files, "plugins/android-wizardTemplate-impl/lib", ".jar") +
        my_glob(files, "plugins/android-ndk/lib", ".jar") +
        my_glob(files, "plugins/sdk-updates/lib", ".jar")
    )
    native.java_import(
        name = "android",
        jars = android_jars,
        tags = ["incomplete-deps"],
        runtime_deps = [
            ":kotlin",
        ],
    )
    unpacked += android_jars

    # The plugins required by ASwB. We need to include them
    # when running integration tests.
    bundled_plugins_jars = (
        my_glob(files, "plugins/gradle/lib", ".jar") +
        my_glob(files, "plugins/gradle-java/lib", ".jar") +
        my_glob(files, "plugins/Groovy/lib", ".jar") +
        my_glob(files, "plugins/java-i18n/lib", ".jar") +
        my_glob(files, "plugins/junit/lib", ".jar") +
        my_glob(files, "plugins/platform-langInjection/lib", ".jar") +
        my_glob(files, "plugins/properties/lib", ".jar") +
        my_glob(files, "plugins/smali/lib", ".jar") +
        my_glob(files, "plugins/toml/lib", ".jar") +
        my_glob(files, "plugins/webp/lib", ".jar")
    )
    bundled_plugins_data = my_glob(files, "plugins/design-tools/resources/layoutlib", "", recursive = True)
    native.java_import(
        name = "bundled_plugins",
        testonly = 1,
        data = bundled_plugins_data,
        jars = bundled_plugins_jars,
        tags = [
            "incomplete-deps",
            "intellij-provided-by-sdk",
        ],
    )
    unpacked += bundled_plugins_jars + bundled_plugins_data

    test_recorder_jars = my_glob(files, "plugins/test-recorder/lib", ".jar")
    native.java_import(
        name = "test_recorder",
        jars = test_recorder_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += test_recorder_jars

    coverage_jars = my_glob(files, "plugins/java-coverage/lib", ".jar")
    native.java_import(
        name = "coverage",
        jars = coverage_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += coverage_jars

    junit_jars = my_glob(files, "plugins/junit/lib", ".jar")
    native.java_import(
        name = "junit",
        jars = junit_jars,
        tags = ["incomplete-deps"],
    )
    # unpacked += junit_jars # Already present in the bundled_plugins

    sdk_import_jars = my_glob(files, "lib", ".jar", exclude = [
        "lib/junit4.jar",  # Plugin code shouldn't need junit, and plugin tests may be driven by a different version.
        "lib/junit.jar",  # Exclude to avoid warnings: "Multiple versions of JUnit detected on classpath".
        "lib/testFramework.jar",
    ])  # b/183925215: mockito-extensions needs to be removed from these jars.
    native.java_import(
        name = "sdk_import",
        jars = sdk_import_jars,
        tags = [
            "incomplete-deps",
            "intellij-provided-by-sdk",
        ],
        deps = [
            # guava v20+ requires this at compile-time when using annotation processors.
            "@error_prone_annotations//jar",
        ],
    )
    unpacked += sdk_import_jars

    cidr_plugins_jars = (
        my_glob(files, "plugins/c-plugin/lib", ".jar") +  # com.intellij.cidr.lang: C/C++ Language Support
        my_glob(files, "plugins/c-clangd-plugin/lib", ".jar") +  # required for NDK debugging
        my_glob(files, "plugins/cidr-base-plugin/lib", ".jar") +  # com.intellij.cidr.base: CIDR Base
        my_glob(files, "plugins/cidr-debugger-plugin/lib", ".jar")  # com.jetbrains.cidr.execution.debugger: CIDR Debugger
    )
    native.java_import(
        name = "cidr_plugins",
        jars = cidr_plugins_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += cidr_plugins_jars

    guava_jars = ["android-studio/lib/lib.jar"]
    native.java_import(
        name = "guava",
        jars = guava_jars,
        tags = ["incomplete-deps"],
    )
    # unpacked += guava_jars # Already extracted in sdk_import

    native.java_library(
        name = "sdk",
        exports = [
            ":jars_without_mockito_extensions",
            ":sdk_import",
        ],
    )

    no_mockito_extensions_jars = ["android-studio/lib/testFramework.jar"]
    no_mockito_extensions(
        name = "jars_without_mockito_extensions",
        jars = no_mockito_extensions_jars,
        tags = ["incomplete-deps"],
    )
    unpacked += no_mockito_extensions_jars

    native.filegroup(
        name = "application_info_json",
        srcs = ["android-studio/product-info.json"],
    )
    unpacked.append("android-studio/product-info.json")

    native.filegroup(
        name = "kotlinc_version",
        srcs = ["android-studio/plugins/Kotlin/kotlinc/build.txt"],
    )
    unpacked.append("android-studio/plugins/Kotlin/kotlinc/build.txt")

    return unpacked
