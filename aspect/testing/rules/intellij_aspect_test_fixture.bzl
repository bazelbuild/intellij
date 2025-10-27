"""Rules for writing tests for the IntelliJ aspect."""

load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_java//java:defs.bzl", "java_test")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("//aspect:intellij_info.bzl", "intellij_info_aspect")
load("//aspect:intellij_info_impl.bzl", "IntelliJInfo", "update_set_in_dict")

def _impl(ctx):
    """Implementation method for _intellij_aspect_test_fixture."""
    output_groups = dict()
    inputs = depset()
    deps = [dep for dep in ctx.attr.deps if IntelliJInfo in dep]
    for dep in deps:
        for k, v in dep[IntelliJInfo].output_groups.items():
            update_set_in_dict(output_groups, k, v)
            inputs = depset(
                [f for f in v.to_list() if f.short_path.endswith(".intellij-info.txt")],
                transitive = [inputs],
            )

    output_name = ctx.attr.output
    output = ctx.actions.declare_file(output_name)

    args = [output.path]
    args.append(":".join([f.path for f in inputs.to_list()]))
    for k, v in output_groups.items():
        args.append(k)
        args.append(":".join([f.short_path for f in v.to_list()]))
    argfile = ctx.actions.declare_file(
        output_name + ".params",
    )

    ctx.actions.write(output = argfile, content = "\n".join(args))
    ctx.actions.run(
        inputs = inputs.to_list() + [argfile],
        outputs = [output],
        executable = ctx.executable._intellij_aspect_test_fixture_builder,
        arguments = ["@" + argfile.path],
        mnemonic = "IntellijAspectTestFixtureBuilder",
        progress_message = "Building Intellij Aspect Test Fixture",
    )
    return DefaultInfo(
        files = depset([output]),
        runfiles = ctx.runfiles(
            files = [output],
        ),
    )

_intellij_aspect_test_fixture = rule(
    _impl,
    attrs = {
        "deps": attr.label_list(aspects = [intellij_info_aspect]),
        "output": attr.string(mandatory = True),
        "_intellij_aspect_test_fixture_builder": attr.label(
            default = Label("//aspect/testing/rules:IntellijAspectTestFixtureBuilder"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
    },
)

def intellij_aspect_test_fixture(name, deps, transitive_configs = []):
    """
    Runs the aspect on `deps` and writes the output to a file.
    """

    _intellij_aspect_test_fixture(
        name = name,
        output = name + ".intellij-aspect-test-fixture",
        deps = deps,
        testonly = 1,
        transitive_configs = transitive_configs,
    )

def intellij_aspect_test(name, aspect_deps, **kwargs):
    """
    Creates an intellij aspect test. Runs the aspect on `aspect_deps` and makes
    the result available as a fixture called <name>_fixture. The fixture can be
    loaded in the test using the IntellijAspectResource:

    @Rule
    @JvmField
    val aspect: IntellijAspectResource = IntellijAspectResource(this::class.java)
    """

    deps = list(kwargs.pop("deps", []))
    deps.extend([
        "//aspect/testing/rules:IntellijAspectResource",
        "//intellij_platform_sdk:test_libs",
        "//proto:common_java_proto",
        "//third_party/java/junit",
    ])

    data = list(kwargs.pop("data", []))
    data.append(name + "_fixture")

    intellij_aspect_test_fixture(
        name = name + "_fixture",
        deps = aspect_deps,
    )

    kt_jvm_library(
        name = name + "_lib",
        testonly = 1,
        data = data,
        deps = deps,
        **kwargs
    )

    java_test(
        name = name,
        runtime_deps = [name + "_lib"],
    )
