load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load(
    "//:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "FAST_BUILD_JAVAC_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "stamped_plugin_xml",
)
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

_FAST_BUILD_JAVAC_SRCS = ["src/com/google/idea/blaze/java/fastbuild/FastBuildJavac.java"]

# This interface is used by two classloaders (the plugin's and the compiler's) whose only shared
# classes are those from the JRE, so it can't have any dependencies at all!
java_library(
    name = "fast_build_javac_interface",
    srcs = _FAST_BUILD_JAVAC_SRCS,
)

_FAST_BUILD_JAVAC_IMPL_SRCS = [
    "src/com/google/idea/blaze/java/fastbuild/FastBuildJavacImpl.java",
    "src/com/google/idea/blaze/java/fastbuild/FormattedJavacDiagnostic.java",
]

java_library(
    name = "java",
    srcs = glob(
        ["src/**/*.java"],
        exclude = _FAST_BUILD_JAVAC_SRCS + _FAST_BUILD_JAVAC_IMPL_SRCS,
    ),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":fast_build_javac_interface",
        "//base",
        "//base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//base/src/com/google/idea/blaze/base/project/startup",
        "//common/actions",
        "//common/experiments",
        "//common/settings",
        "//common/util:concurrency",
        "//common/util:platform",
        "//common/util:transactions",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:junit",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//querysync",
        "//sdkcompat",
        "//shared",
        "//shared:artifact",
        "//third_party/java/auto_value",
        "@error_prone_annotations//jar",
    ],
)

# Packaged with the plugin as a separate jar.
java_library(
    name = "fast_build_javac",
    srcs = _FAST_BUILD_JAVAC_SRCS + _FAST_BUILD_JAVAC_IMPL_SRCS,
    javacopts = [
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    ],
    visibility = FAST_BUILD_JAVAC_VISIBILITY,
)

optional_plugin_xml(
    name = "optional_java",
    module = ["JUnit"],
    plugin_xml = "src/META-INF/java-contents.xml",
)

optional_plugin_xml(
    name = "optional_coverage",
    module = ["com.intellij.modules.coverage"],
    plugin_xml = "src/META-INF/coverage-contents.xml",
)

optional_plugin_xml(
    name = "optional_java_coverage",
    module = [
        "com.intellij.modules.coverage",
        "com.intellij.java",
    ],
    plugin_xml = "src/META-INF/java-coverage-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [
        ":optional_java",
        ":optional_coverage",
        ":optional_java_coverage",
    ],
    plugin_xmls = ["src/META-INF/blaze-java.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":java"],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    # Used for the compiler tests
    data = [
        ":auto_value_lib_deploy.jar",
        ":auto_value_plugin_lib_deploy.jar",
        ":fast_build_javac",
        ":guava_lib_deploy.jar",
        ":truth_lib_deploy.jar",
    ],
    jvm_flags = [
        "-Dauto_value.jar=$(location :auto_value_lib_deploy.jar)",
        "-Dauto_value_plugin.jar=$(location :auto_value_plugin_lib_deploy.jar)",
        "-Dfast_build_javac.jar=$(location :fast_build_javac)",
        "-Dguava.jar=$(location :guava_lib_deploy.jar)",
        "-Dtruth.jar=$(location :truth_lib_deploy.jar)",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    ],
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.java",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":java",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//intellij_platform_sdk:truth",
        "//proto:proto_deps",
        "//shared",
        "//testing:lib",
        "//third_party/java/junit",
        "@com_google_guava_guava//jar",
        "@org_opentest4j_opentest4j//jar",
    ],
)

java_binary(
    name = "auto_value_lib",
    main_class = "None",
    runtime_deps = ["//third_party/java/auto_value"],
)

java_binary(
    name = "auto_value_plugin_lib",
    main_class = "None",
    runtime_deps = ["//third_party/java/auto_value"],
)

java_binary(
    name = "guava_lib",
    testonly = True,
    main_class = "None",
    runtime_deps = [
        "//intellij_platform_sdk:guava",
    ],
)

java_binary(
    name = "truth_lib",
    testonly = 1,
    main_class = "None",
    runtime_deps = [
        "//intellij_platform_sdk:truth",
    ],
)

stamped_plugin_xml(
    name = "java_plugin_xml",
    testonly = 1,
    plugin_id = "com.google.idea.blaze.java",
    plugin_name = "com.google.idea.blaze.java",
)

intellij_plugin(
    name = "java_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":java_plugin_xml",
    deps = [
        ":plugin_library",
        "//base:plugin_library",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(
        ["tests/integrationtests/**/*.java"],
    ),
    required_plugins = "com.google.idea.blaze.java",
    tags = [
        "noci:studio-win",
    ],
    test_package_root = "com.google.idea.blaze.java",
    visibility = DEFAULT_TEST_VISIBILITY,
    runtime_deps = [
        ":java_integration_test_plugin",
    ],
    deps = [
        ":java",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//proto:proto_deps",
        "//querysync",
        "//shared",
        "//third_party/java/junit",
        "//third_party/java/truth",
        "@com_google_guava_guava//jar",
        "@org_opentest4j_opentest4j//jar",
    ],
)
