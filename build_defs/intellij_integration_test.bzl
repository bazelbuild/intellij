load("@rules_java//java:defs.bzl", "java_test")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load(":common.bzl", "intellij_common")

_JVM_ADD_OPENS_FLAGS = [
    "--add-opens=%s=ALL-UNNAMED" % x
    for x in [
        # keep sorted
        "java.base/java.io",
        "java.base/java.lang",
        "java.base/java.nio",
        "java.base/java.util",
        "java.base/java.util.concurrent",
        "java.base/jdk.internal.vm",
        "java.base/sun.nio.fs",
        "java.desktop/java.awt",
        "java.desktop/java.awt.event",
        "java.desktop/javax.swing",
        "java.desktop/javax.swing.plaf.basic",
        "java.desktop/javax.swing.text",
        "java.desktop/javax.swing.text.html",
        "java.desktop/javax.swing.text.html.parser",
        "java.desktop/sun.awt",
        "java.desktop/sun.awt.image",
        "java.desktop/sun.font",
        "java.desktop/sun.swing",
    ]
]

_JVM_INTELLIJ_CONFIG_FLAGS = [
    "-Didea.classpath.index.enabled=false",
    "-Didea.force.use.core.classloader=true",
    "-Djava.awt.headless=true",
    "-Didea.suppressed.plugins.set.classic=org.jetbrains.plugins.clion.radler,intellij.rider.cpp.debugger,intellij.rider.plugins.clion.radler.cwm",
    "-Didea.suppressed.plugins.set.selector=classic",
    "-DNO_FS_ROOTS_ACCESS_CHECK=true",
]

def intellij_integration_test(test, deps = None, jvm_flags = None, test_package = None, **kwargs):
    """
    Crates a JUnit4 integration test for a single Kotlin class with a dependency
    on the plugin API. Use the TestSandbox rule to setup the test environment
    inside the test.

    Prefer this over the old intellij_integration_test_suite, as it works for Kotlin
    test classes and provides a more fine grained mapping.
    """

    name = test.removesuffix(".kt")

    deps = deps or []

    kt_jvm_library(
        name = name + "_ktlib",
        srcs = [test],
        deps = deps + [
            "//intellij_platform_sdk:plugin_api_for_tests",
            "//third_party/java/truth",
            "//testing/src/com/google/idea/testing/integration",
            "@maven//:org_opentest4j_opentest4j",
        ],
        testonly = 1,
        visibility = ["//visibility:private"],
    )

    jvm_flags = jvm_flags or []
    jvm_flags.extend(_JVM_ADD_OPENS_FLAGS)
    jvm_flags.extend(_JVM_INTELLIJ_CONFIG_FLAGS)

    java_test(
        name = name,
        runtime_deps = [
            name + "_ktlib",
            "//intellij_platform_sdk:bundled_plugins",
        ],
        test_class = intellij_common.derive_test_class(name, "integrationtests", test_package),
        jvm_flags = jvm_flags,
        **kwargs
    )
