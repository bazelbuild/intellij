"""Utility methods for working with ArtifactLocation types."""

def struct_omit_none(**kwargs):
    """A replacement for standard `struct` function that omits the fields with None value."""
    d = {name: kwargs[name] for name in kwargs if kwargs[name] != None}
    return struct(**d)

def sources_from_target(ctx):
    """Get the list of sources from a target as artifact locations."""
    return artifacts_from_target_list_attr(ctx, "srcs")

def artifacts_from_target_list_attr(ctx, attr_name):
    """Converts a list of targets to a list of artifact locations."""
    return [
        artifact_location(f)
        for target in getattr(ctx.rule.attr, attr_name, [])
        for f in target.files.to_list()
    ]

def artifact_location(f):
    """Creates an ArtifactLocation proto from a File."""
    if f == None:
        return None

    relative_path = _strip_external_workspace_prefix(f.short_path)
    relative_path = _strip_root_path(relative_path, f.root.path)

    root_path = f.path[:-(len("/" + relative_path))]

    return to_artifact_location(
        root_path = root_path,
        relative_path = relative_path,
        is_source = f.is_source,
        is_external = is_external_artifact(f.owner),
    )

def to_artifact_location(root_path, relative_path, is_source, is_external):
    """Creates creates an ArtifactLocation proto."""

    return struct_omit_none(
        relative_path = relative_path,
        root_path = root_path,
        is_source = is_source,
        is_external = is_external,
    )

def is_external_artifact(label):
    """Determines whether a label corresponds to an external artifact."""
    return label.workspace_root.startswith("external/")

def _strip_root_path(path, root_path):
    """Strips the root_path from the path."""

    if root_path and path.startswith(root_path + "/"):
        return path[len(root_path + "/"):]
    else:
        return path

def _strip_external_workspace_prefix(path):
    """Strips '../workspace_name/' prefix."""

    if path.startswith("../"):
        return "/".join(path.split("/")[2:])
    else:
        return path
