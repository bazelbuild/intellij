"""Rules for writing tests for the IntelliJ aspect."""

load(
    "//aspect:fast_build_info.bzl",
    "fast_build_info_aspect",
)

def _impl(ctx):
    """Implementation method for _fast_build_aspect_test_fixture."""

    deps = [dep for dep in ctx.attr.deps if "ide-fast-build" in dep[OutputGroupInfo]]
    inputs = depset(transitive = [dep[OutputGroupInfo]["ide-fast-build"] for dep in deps])

    output_name = ctx.attr.output
    output = ctx.actions.declare_file(output_name)

    args = [output.path]
    args += [f.path for f in inputs.to_list()]
    argfile = ctx.actions.declare_file(
        output_name + ".params",
    )

    ctx.actions.write(output = argfile, content = "\n".join(args))
    ctx.actions.run(
        inputs = inputs.to_list() + [argfile],
        outputs = [output],
        executable = ctx.executable._fast_build_aspect_test_fixture_builder,
        arguments = ["@" + argfile.path],
        mnemonic = "FastBuildAspectTestFixtureBuilder",
        progress_message = "Building Fast Build Aspect Test Fixture",
    )
    return DefaultInfo(
        files = depset([output]),
        runfiles = ctx.runfiles(
            files = [output],
        ),
    )

_fast_build_aspect_test_fixture = rule(
    _impl,
    attrs = {
        "deps": attr.label_list(aspects = [fast_build_info_aspect]),
        "output": attr.string(mandatory = True),
        "_fast_build_aspect_test_fixture_builder": attr.label(
            default = Label("//aspect/testing/rules:FastBuildAspectTestFixtureBuilder"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
    },
)

def fast_build_aspect_test_fixture(name, deps):
    _fast_build_aspect_test_fixture(
        name = name,
        output = name + ".fast-build-aspect-test-fixture",
        deps = deps,
        testonly = 1,
    )
