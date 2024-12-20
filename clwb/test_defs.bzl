load(
    "@bazel_binaries//:defs.bzl",
    "bazel_binaries",
)
load(
    "@rules_bazel_integration_test//bazel_integration_test:defs.bzl",
    "bazel_integration_test",
    "bazel_integration_tests",
    "integration_test_utils",
)
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
)

def _repacked_files_impl(ctx):
    outputs = []

    for target in ctx.attr.srcs:
        for file in target.files.to_list():
            link = ctx.actions.declare_file("%s/%s" % (ctx.attr.prefix, file.basename))
            outputs.append(link)

            ctx.actions.symlink(output = link, target_file = file)

    return [DefaultInfo(files = depset(outputs))]

repacked_files = rule(
    implementation = _repacked_files_impl,
    attrs = {
        "srcs": attr.label_list(mandatory = True),
        "prefix": attr.string(mandatory = True),
    },
)

def clwb_integration_test(name, project, srcs, deps = []):
    repacked_files(
        name = name + "_aspect_files",
        srcs = ["//aspect:aspect_files"],
        prefix = "aspect",
    )

    repacked_files(
        name = name + "_aspect_template_files",
        srcs = ["//aspect:aspect_template_files"],
        prefix = "aspect_template",
    )

    runner = name + "_runner"

    intellij_integration_test_suite(
        name = runner,
        srcs = srcs + native.glob(["tests/integrationtests/com/google/idea/blaze/clwb/base/*.java"]),
        test_package_root = "com.google.idea.blaze.clwb",
        runtime_deps = [":clwb_bazel"],
        data = [
            name + "_aspect_files",
            name + "_aspect_template_files",
        ],
        jvm_flags = [
            # disables the default bazel security manager, causes tests to fail on windows
            "-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false",
            # fixes preferences not writable on mac
            "-Djava.util.prefs.PreferencesFactory=com.google.idea.blaze.clwb.base.InMemoryPreferencesFactory",
            # suppressed plugin sets for classic, radler is currently disabled for tests
            "-Didea.suppressed.plugins.set.classic=org.jetbrains.plugins.clion.radler,intellij.rider.cpp.debugger,intellij.rider.plugins.clion.radler.cwm",
            "-Didea.suppressed.plugins.set.selector=classic",
            # define the path to the query sync aspects
            "-Dblaze.idea.build_dependencies.bzl.file=clwb/aspect/build_dependencies.bzl",
            "-Dblaze.idea.build_dependencies_deps.bzl.file=clwb/aspect/build_dependencies_deps.bzl",
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
            "//third_party/java/junit",
            "@org_opentest4j_opentest4j//jar",
        ],
    )

    for version in bazel_binaries.versions.all:
        bazel_integration_test(
            name = integration_test_utils.bazel_integration_test_name(name, version),
            tags = [],
            bazel_version = version,
            test_runner = runner,
            workspace_path = "tests/projects/" + project,
            env = {
                # disables automatic conversion of bazel target names to absolut windows paths by msys
                "MSYS_NO_PATHCONV": "true",
                # pass the bazel version to the test for RuleBazelVersion
                "BIT_BAZEL_VERSION": version,
            },
            # inherit bash shell and visual studio path from host for windows
            additional_env_inherit = ["BAZEL_SH", "BAZEL_VC"],
        )

    native.test_suite(
        name = name,
        tags = ["manual"],
        tests = integration_test_utils.bazel_integration_test_names(
            name,
            bazel_binaries.versions.all,
        ),
    )
