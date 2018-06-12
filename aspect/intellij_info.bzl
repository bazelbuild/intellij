"""Bazel-specific intellij aspect."""

load(
    ":intellij_info_impl.bzl",
    "intellij_info_aspect_impl",
    "make_intellij_info_aspect",
)

def tool_label(tool_name):
    """Returns a label that points to a tool target in the bundled aspect workspace."""
    return Label("//aspect/tools:" + tool_name)

def get_go_import_path(ctx):
    """Returns the import path for a go target."""
    import_path = getattr(ctx.rule.attr, "importpath", None)
    if import_path:
        return import_path
    prefix = None
    if hasattr(ctx.rule.attr, "_go_prefix"):
        prefix = ctx.rule.attr._go_prefix.go_prefix
    if not prefix:
        return None
    import_path = prefix
    if ctx.label.package:
        import_path += "/" + ctx.label.package
    if ctx.label.name != "go_default_library":
        import_path += "/" + ctx.label.name
    return import_path

semantics = struct(
    tool_label = tool_label,
    go = struct(
        get_import_path = get_go_import_path,
    ),
)

def _aspect_impl(target, ctx):
    return intellij_info_aspect_impl(target, ctx, semantics)

intellij_info_aspect = make_intellij_info_aspect(_aspect_impl, semantics)
