load("@rules_cc//cc:defs.bzl", "CcInfo")

DEPS = [
    "_stl",  # from cc rules
    "malloc",  # from cc_binary rules
    "deps",
    "implementation_deps",
]

CcDepInfo = provider(
    fields = ["targets"],
)

def _flatten(xss):
    return [x for xs in xss for x in xs]

def _attr_get_target(attr, name):
    """Returns the value of a target attribute or a target list attribute."""
    value = getattr(attr, name, None)
    value_type = type(value)

    if value_type == "Target":
        return [value]
    elif value_type == "list":
        return value
    else:
        return []

def _attr_get_targets(attr, names):
    """Returns the value of multiple target attributes as a list."""
    return _flatten([_attr_get_target(attr, name) for name in names])

def module_transitive_cc_dependencies_collect(ctx, target):
    """Collects all transitive dependencies in of a target."""
    transitive = [dep[CcDepInfo].targets for dep in _attr_get_targets(ctx.rule.attr, DEPS) if CcDepInfo in dep]

    if CcInfo in target:
        local = [target]
    else:
        local = []

    targets = depset(local, transitive = transitive)

    return struct(
      providers = [CcDepInfo(targets = targets)],
      targets = targets.to_list(),
    )
