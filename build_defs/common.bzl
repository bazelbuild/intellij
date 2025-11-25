load("@bazel_skylib//lib:paths.bzl", "paths")

def _compute_plugin_layout(prefix, targets):
    """Computes the plugin layout from the dependencies."""
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
