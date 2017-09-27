"""Bazel-specific intellij aspect."""

load(
    ":intellij_info_impl.bzl",
    "make_intellij_info_aspect",
    "intellij_info_aspect_impl",
)

def tool_label(tool_name):
  """Returns a label that points to a tool target in the bundled aspect workspace."""
  return Label("//aspect/tools:" + tool_name)

semantics = struct(
    tool_label = tool_label,
)

def _aspect_impl(target, ctx):
  return intellij_info_aspect_impl(target, ctx, semantics)

intellij_info_aspect = make_intellij_info_aspect(_aspect_impl, semantics)
