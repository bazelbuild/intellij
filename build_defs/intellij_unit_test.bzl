load("@rules_java//java:defs.bzl", "java_test")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

laod(":common.bzl", "intellij_common")

def intellij_unit_test(test, deps = None, test_package = None, **kwargs):
    """
    Crates a JUnit4 unit test for a single Kotlin class with a dependency on
    the plugin API.

    Prefer this over the old intellij_unit_test_suite, as it works for Kotlin
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
        ],
        testonly = 1,
        visibility = ["//visibility:private"],
    )

    java_test(
        name = name,
        runtime_deps = [name + "_ktlib"],
        test_class = intellij_common.derive_test_class(name, "unittests", test_package),
        **kwargs
    )
