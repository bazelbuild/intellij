load("@bazel_skylib//lib:paths.bzl", "paths")

def _update_plugin_layout(mapping, path, file):
    """Helper function to detect duplicate entries in the plugin zip."""
    if path in mapping:
        fail("duplicate entry in plugin layout:", path)

    mapping[path] = file

def _compute_plugin_layout(prefix, targets):
    """Computes the plugin layout from the target list.

    All files of the targets are installed into the lib directory, this should only be jars. Runfiles
    of the targets are installed directly into the root directory. However, all symlink mappings
    are respected.
    """
    mapping = {}

    for target in targets:
        info = target[DefaultInfo]

        for file in info.files.to_list():
            _update_plugin_layout(mapping, paths.join(prefix, "lib", file.basename), file)

        for link in info.default_runfiles.symlinks.to_list():
            _update_plugin_layout(mapping, paths.join(prefix, link.path), link.target_file)

        for file in info.default_runfiles.files.to_list():
            _update_plugin_layout(mapping, paths.join(prefix, file.short_path), file)

    return mapping

def _derive_test_class(class_name, test_directory, package = None):
    """Derives the fully qualified test class name.

    If the package name is not provided explicitly the package name is derived
    from the current Bazel package.
    """
    if package:
        return "%s.%s" % (package, class_name)

    parts = native.package_name().split("/")

    if test_directory not in parts:
        fail("cannot derive test package name for:", class_name)

    start = parts.index(test_directory)
    return ".".join(parts[start + 1:] + [class_name])

intellij_common = struct(
    compute_plugin_layout = _compute_plugin_layout,
    derive_test_class = _derive_test_class,
)
