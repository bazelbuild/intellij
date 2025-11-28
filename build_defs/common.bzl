load("@bazel_skylib//lib:paths.bzl", "paths")

def _compute_plugin_layout(prefix, targets):
    """Computes the plugin layout from the target list.

    All files of the targets are install into the lib directory, this should only be jars. Runfiles
    of the targets are installed directly into the root directory. However, all symlink mappings
    are respected.
    """
    mapping = {}

    for target in targets:
        info = target[DefaultInfo]

        for file in info.files.to_list():
            mapping[paths.join(prefix, "lib", file.basename)] = file

        for link in info.default_runfiles.symlinks.to_list():
            mapping[paths.join(prefix, link.path)] = link.target_file

        for file in info.default_runfiles.files.to_list():
            mapping[paths.join(prefix, file.path)] = file

    return mapping

intellij_common = struct(
    compute_plugin_layout = _compute_plugin_layout,
)
