load("@rules_java//java:defs.bzl", "java_common")

def _grammar_kit_lexer_impl(ctx):
    runtime = ctx.attr._runtime[java_common.JavaRuntimeInfo]

    output = ctx.actions.declare_file(ctx.attr.class_name + ".java")

    ctx.actions.run_shell(
        tools = runtime.files,
        inputs = [ctx.file.src, ctx.file._jar, ctx.file._skeleton],
        outputs = [output],
        command = "{java} -jar {jar} --quiet --skel {sekeleton} -d {output} {src}".format(
            java = runtime.java_executable_exec_path,
            jar = ctx.file._jar.path,
            sekeleton = ctx.file._skeleton.path,
            output = output.dirname,
            src = ctx.file.src.path,
        ),
        mnemonic = "GrammarKitLexer",
        progress_message = "Generating lexer for %{label}",
    )

    return [DefaultInfo(files = depset([output]))]

grammar_kit_lexer = rule(
    doc = "Generates a JFlex Java lexer from a .flex file.",
    implementation = _grammar_kit_lexer_impl,
    attrs = {
        "src": attr.label(
            doc = "The .flex file to process.",
            allow_single_file = [".flex"],
            mandatory = True,
        ),
        "class_name": attr.string(
            doc = "The name of the Java class as specified in the .flex file.",
            mandatory = True,
        ),
        "_skeleton": attr.label(
            allow_single_file = True,
            default = Label("//third_party/grammar_kit:idea-flex.skeleton"),
        ),
        "_jar": attr.label(
            default = Label("//third_party/grammar_kit:jflex_deploy.jar"),
            allow_single_file = [".jar"],
        ),
        "_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
        ),
    },
)
