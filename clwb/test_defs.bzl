load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
)
load(
    "@rules_bazel_integration_test//bazel_integration_test:defs.bzl",
    "bazel_integration_test",
    "bazel_integration_tests",
    "integration_test_utils",
)
load("@bazel_binaries//:defs.bzl", "bazel_binaries")

# version specific flags can be removed if version is dropped in MODULE.bazel
version_specific_args = {
    "5.4.1": {
        # mac CI runners ship invalid flags in system rc file
        "startup_options": "--nosystem_rc",
     },
}

def clwb_integration_test(name, project, srcs, deps = []):
    runner = name + "_runner"

    intellij_integration_test_suite(
        name = runner,
        srcs = srcs + native.glob(["tests/integrationtests/com/google/idea/blaze/clwb/base/*.java"]),
        test_package_root = "com.google.idea.blaze.clwb",
        runtime_deps = [":clwb_bazel"],
        data = ["//aspect:aspect_files"],
        jvm_flags = [
            # disables the default bazel security manager, causes tests to fail on windows
            "-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false",
            # fixes preferences not writable on mac
            "-Djava.util.prefs.PreferencesFactory=com.google.idea.blaze.clwb.base.InMemoryPreferencesFactory",
        ],
        deps = deps + [
            ":clwb_lib",
            "//base",
            "//shared",
            "//common/util:process",
            "//intellij_platform_sdk:jsr305",
            "//intellij_platform_sdk:plugin_api_for_tests",
            "//intellij_platform_sdk:test_libs",
            "//sdkcompat",
            "@junit//jar",
            "@org_opentest4j_opentest4j//jar",
        ],
    )

    for version in bazel_binaries.versions.all:
        bazel_integration_test(
            name = integration_test_utils.bazel_integration_test_name(name, version),
            tags = [],
            bazel_version = version,
            test_runner = ":" + runner,
            workspace_path = "tests/projects/" + project,
            # disables automatic conversion of bazel target names to absolut windows paths by msys
            env = {"MSYS_NO_PATHCONV": "true"},
            # inherit bash shell and visual studio path from host for windows
            additional_env_inherit = ["BAZEL_SH", "BAZEL_VC"],
            # add version specific arguments, since some older versions cannot handle newer flags
            **version_specific_args.get(version, {}),
        )

    native.test_suite(
        name = name,
        tags = ["manual"],
        tests = integration_test_utils.bazel_integration_test_names(
            name,
            bazel_binaries.versions.all,
        ),
    )
