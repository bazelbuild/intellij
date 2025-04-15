load(
    "//testing:test_defs.bzl",
    "bazel_integration_tests",
    "intellij_integration_test_suite",
)

def ijwb_headless_test(name, project, srcs, deps = []):
    runner = name + "_runner"

    intellij_integration_test_suite(
        name = runner,
        srcs = srcs + native.glob(["tests/headlesstests/com/google/idea/blaze/ijwb/headless/base/*.java"]),
        test_package_root = "com.google.idea.blaze.ijwb",
        runtime_deps = [":ijwb_bazel"],
        jvm_flags = [
            # disables the default bazel security manager, causes tests to fail on windows
            "-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false",
            # fixes preferences not writable on mac
            "-Djava.util.prefs.PreferencesFactory=com.google.idea.testing.headless.InMemoryPreferencesFactory",
        ],
        deps = deps + [
            ":ijwb_lib",
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

    bazel_integration_tests(
        name = name,
        test_runner = runner,
        workspace_path = "tests/projects/" + project,
        # inherit bash shell and visual studio path from host for windows
        additional_env_inherit = ["BAZEL_SH", "BAZEL_VC"],
    )
