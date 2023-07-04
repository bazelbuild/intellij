"""IntelliJ plugin debug target rule used for debugging IntelliJ plugins.

Creates a plugin target debuggable from IntelliJ. Any files in
the 'deps' and 'javaagents' attribute are deployed to the plugin sandbox.

Any files are stripped of their prefix and installed into
<sandbox>/plugins. If you need structure, first put the files
into //build_defs:build_defs%repackage_files.

intellij_plugin_debug_targets can be nested.

repackaged_files(
  name = "foo_files",
  srcs = [
    ":my_plugin_jar",
    ":my_additional_plugin_files",
  ],
  prefix = "plugins/foo/lib",
)

intellij_plugin_debug_target(
  name = "my_debug_target",
  deps = [
    ":my_jar",
  ],
  javaagents = [
    ":agent_deploy.jar",
  ],
)

"""

load("//build_defs:build_defs.bzl", "output_path", "repackaged_files_data")

SUFFIX = ".intellij-plugin-debug-target-deploy-info"

def _repackaged_deploy_file(f, repackaging_data):
    return struct(
        src = f,
        deploy_location = output_path(f, repackaging_data),
    )

def _flat_deploy_file(f):
    return struct(
        src = f,
        deploy_location = f.basename,
    )

def _intellij_plugin_debug_target_aspect_impl(target, ctx):
    aspect_intellij_plugin_deploy_info = None

    files = target.files
    if ctx.rule.kind == "intellij_plugin_debug_target":
        aspect_intellij_plugin_deploy_info = target.intellij_plugin_deploy_info
    elif ctx.rule.kind == "_repackaged_files":
        data = target[repackaged_files_data]
        aspect_intellij_plugin_deploy_info = struct(
            deploy_files = [_repackaged_deploy_file(f, data) for f in data.files.to_list()],
            java_agent_deploy_files = [],
        )

        # TODO(brendandouglas): Remove when migrating to Bazel 0.5, when DefaultInfo
        # provider can be populated by '_repackaged_files' directly
        files = depset(transitive = [files, data.files])
    else:
        aspect_intellij_plugin_deploy_info = struct(
            deploy_files = [_flat_deploy_file(f) for f in target.files.to_list()],
            java_agent_deploy_files = [],
        )
    return struct(
        input_files = files,
        aspect_intellij_plugin_deploy_info = aspect_intellij_plugin_deploy_info,
    )

_intellij_plugin_debug_target_aspect = aspect(
    implementation = _intellij_plugin_debug_target_aspect_impl,
)

def _build_deploy_info_file(deploy_file):
    return struct(
        execution_path = deploy_file.src.path,
        deploy_location = deploy_file.deploy_location,
    )

def _intellij_plugin_debug_target_impl(ctx):
    files = depset()
    deploy_files = []
    java_agent_deploy_files = []
    for target in ctx.attr.deps:
        files = depset(transitive = [files, target.input_files])
        deploy_files.extend(target.aspect_intellij_plugin_deploy_info.deploy_files)
        java_agent_deploy_files.extend(target.aspect_intellij_plugin_deploy_info.java_agent_deploy_files)
    for target in ctx.attr.javaagents:
        files = depset(transitive = [files, target.input_files])
        java_agent_deploy_files.extend(target.aspect_intellij_plugin_deploy_info.deploy_files)
        java_agent_deploy_files.extend(target.aspect_intellij_plugin_deploy_info.java_agent_deploy_files)
    deploy_info = struct(
        deploy_files = [_build_deploy_info_file(f) for f in deploy_files],
        java_agent_deploy_files = [_build_deploy_info_file(f) for f in java_agent_deploy_files],
    )
    output = ctx.actions.declare_file(ctx.label.name + SUFFIX)
    ctx.actions.write(output, proto.encode_text(deploy_info))

    # We've already consumed any dependent intellij_plugin_debug_targets into our own,
    # do not build or report these
    files = depset([f for f in files.to_list() if not f.path.endswith(SUFFIX)])
    files = depset([output], transitive = [files])

    return struct(
        files = files,
        intellij_plugin_deploy_info = struct(
            deploy_files = deploy_files,
            java_agent_deploy_files = java_agent_deploy_files,
        ),
    )

intellij_plugin_debug_target = rule(
    implementation = _intellij_plugin_debug_target_impl,
    attrs = {
        "deps": attr.label_list(aspects = [_intellij_plugin_debug_target_aspect]),
        "javaagents": attr.label_list(aspects = [_intellij_plugin_debug_target_aspect]),
    },
)
