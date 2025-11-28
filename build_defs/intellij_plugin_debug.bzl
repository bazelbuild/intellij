load(":common.bzl", "intellij_common")

SUFFIX = ".intellij-plugin-debug-target-deploy-info"

def _create_deploy_location(path, file):
    return struct(
        execution_path = file.path,
        deploy_location = path,
    )

def _intellij_plugin_debug_target_impl(ctx):
    output = ctx.actions.declare_file(ctx.label.name + SUFFIX)
    layout = intellij_common.compute_plugin_layout(ctx.attr.prefix, ctx.attr.deps)

    deploy_info = struct(
        deploy_files = [_create_deploy_location(path, file) for path, file in layout.items()],
        java_agent_deploy_files = [],
    )

    ctx.actions.write(output, proto.encode_text(deploy_info))

    return [DefaultInfo(files = depset(layout.values() + [output]))]

intellij_plugin_debug_target = rule(
    implementation = _intellij_plugin_debug_target_impl,
    doc = """Creates plugin target debuggable from IntelliJ.

    All files in deps are mapped into the plugin sandbox in the same mannar as they would be mapped
    into the release zip.
    """,
    attrs = {
        "deps": attr.label_list(
            doc = "List of dependencies to deploy to the sandbox.",
            allow_files = False,
            mandatory = True,
        ),
        "prefix": attr.string(
            doc = "The directory name inside the plugins folder.",
            mandatory = True,
        ),
    },
)
