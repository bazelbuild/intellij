load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
)

def _integration_test_suite(name, srcs, deps = []):
    intellij_integration_test_suite(
        name = name,
        srcs = srcs,
        test_package_root = "com.google.idea.blaze.clwb",
        runtime_deps = [":clwb_bazel"],
        jvm_flags = [
            # disables the default bazel security manager, causes tests to fail on windows
            "-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false",
            # fixes preferences not writable on mac
            "-Djava.util.prefs.PreferencesFactory=com.google.idea.testing.headless.InMemoryPreferencesFactory",
            # suppressed plugin sets for classic, radler is currently disabled for tests
            "-Didea.suppressed.plugins.set.classic=org.jetbrains.plugins.clion.radler,intellij.rider.cpp.debugger,intellij.rider.plugins.clion.radler.cwm",
            "-Didea.suppressed.plugins.set.selector=classic",
            # enable detailed logging in tests to diagnose issues in CI
            "-Didea.log.trace.categories=com.jetbrains.cidr.lang.workspace,com.google.idea.blaze.cpp.BlazeCWorkspace",
            "-Dcidr.debugger.use.lldbfrontend.from.plugin=false",
        ],
        deps = deps + [
            "//clwb:plugin_library",
            "//base",
            "//shared",
            "//common/util:process",
            "//intellij_platform_sdk:jsr305",
            "//intellij_platform_sdk:plugin_api_for_tests",
            "//intellij_platform_sdk:test_libs",
            "//sdkcompat",
            "//third_party/java/junit",
            "@org_opentest4j_opentest4j//jar",
            "//testing/src/com/google/idea/testing/headless",
        ],
    )

def clwb_integration_test(name, srcs, deps = []):
    _integration_test_suite(
        name = name,
        srcs = srcs + native.glob([
            "tests/integrationtests/com/google/idea/blaze/clwb/base/*.java",
            "tests/integrationtests/com/google/idea/blaze/clwb/base/*.kt",
        ]),
        deps = deps + ["//cpp"],
    )

