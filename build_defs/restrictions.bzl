"""
Restriction rules for plugin development

This file implements an aspect that restricts the
transitive dependencies of intellij_plugins that
decide to do so.

This prevents large transitive google3 dependencies
from making it into the plugin that runs on
a different context (IntelliJ and not google3)
"""

# intellij_plugin will validate that all dependencies from these pacakages are self contained
_project = [
]

# A set of external dependencies that can be built outside of google3
_valid = [
]

# A temporary list of external targets that plugins are depending on. DO NOT ADD TO THIS
_allowed = [
]

# A list of targets currently with not allowed dependencies
_existing_violations = [
]

def _in_set(target, set):
    pkg = target.label.package
    for p in set:
        if pkg == p or pkg.startswith(p + "/"):
            return True
        if str(target.label) == p:
            return True

    return False

def _in_project(target):
    return _in_set(target, _project)

def _get_deps(ctx):
    if not hasattr(ctx.rule.attr, "deps"):
        return []
    return ctx.rule.attr.deps

def _restricted_deps_aspect_impl(target, ctx):
    if not _in_project(target):
        return []

    outside_project = []
    for d in _get_deps(ctx):
        if not _in_project(d):
            outside_project.append(d)

    invalid = [dep for dep in outside_project if not _in_set(dep, _valid + _allowed)]
    if invalid:
        tgts = ", ".join([str(t.label) for t in invalid])
        error = "Invalid dependencies for target " + str(target.label) + " [" + tgts + "]\n"
        error += "For more information see restrictions.bzl"
        fail(error)

    allowed = [dep for dep in outside_project if _in_set(dep, _allowed)]
    if allowed and not _in_set(target, _existing_violations):
        tgts = ", ".join([str(t.label) for t in allowed])
        error = "Target not allowed to have external dependencies: " + str(target.label) + " [" + tgts + "]\n"
        error += "For more information see restrictions.bzl"
        fail(error)

    return []

restricted_deps_aspect = aspect(
    implementation = _restricted_deps_aspect_impl,
    attr_aspects = ["*"],
)
