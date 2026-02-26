load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")

def _grammar_kit_parser_impl(ctx):
    runtime = ctx.attr._runtime[java_common.JavaRuntimeInfo]

    output = ctx.actions.declare_file(ctx.label.name + ".srcjar")

    ctx.actions.run_shell(
        tools = runtime.files,
        inputs = [ctx.file.src, ctx.file._jar],
        outputs = [output],
        command = "{java} -Didea.home.path={home} -jar {jar} {src} {output}".format(
            java = runtime.java_executable_exec_path,
            jar = ctx.file._jar.path,
            src = ctx.file.src.path,
            output = output.path,
            home = output.dirname,
        ),
        mnemonic = "GrammarKitParser",
        progress_message = "Generating parser for %{label}",
    )

    return [DefaultInfo(files = depset([output]))]

grammar_kit_parser = rule(
    doc = "Generates a Grammar-Kit Java parser and psi files from a .bnf file.",
    implementation = _grammar_kit_parser_impl,
    attrs = {
        "src": attr.label(
            doc = "The .bnf file to process.",
            allow_single_file = [".bnf"],
            mandatory = True,
        ),
        "_jar": attr.label(
            default = Label("//third_party/grammar_kit:grammar_kit_deploy.jar"),
            allow_single_file = [".jar"],
        ),
        "_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
        ),
    },
)
