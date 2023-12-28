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
ALLOWED_EXTERNAL_DEPENDENCIES = [
]

# A list of targets currently with not allowed dependencies
EXISTING_EXTERNAL_VIOLATIONS = [
]

RestrictedInfo = provider(
    doc = "The dependencies, per target, outside the project",
    fields = {
        "dependencies": "A map from target to external dependencies",
    },
)

def _in_set(target, set):
    pkg = target.label.package
    for p in set:
        if pkg == p or pkg.startswith(p + "/"):
            return p
        if str(target.label) == p:
            return p

    return None

def _in_project(target):
    return _in_set(target, _project)

def _get_deps(ctx):
    deps = []
    if hasattr(ctx.rule.attr, "deps"):
        deps.extend(ctx.rule.attr.deps)
    if hasattr(ctx.rule.attr, "exports"):
        deps.extend(ctx.rule.attr.exports)
    return deps

def _restricted_deps_aspect_impl(target, ctx):
    if not _in_project(target):
        return []

    dependencies = {}
    outside_project = []
    for d in _get_deps(ctx):
        if not _in_project(d) and not _in_set(d, _valid):
            outside_project.append(d)
        if RestrictedInfo in d:
            dependencies.update(d[RestrictedInfo].dependencies)

    if outside_project:
        dependencies[target] = outside_project

    return [RestrictedInfo(dependencies = dependencies)]

def validate_restrictions(dependencies, allowed_external, existing_violations):
    violations = sorted([str(d.label) for d in dependencies.keys()])
    if violations != sorted(existing_violations):
        new_violations = [t for t in violations if t not in existing_violations]
        no_longer_violations = [t for t in existing_violations if t not in violations]
        if new_violations:
            fail("These targets now depend on external targets: " + ", ".join(new_violations))
        if no_longer_violations:
            fail("The following targets no longer depend on external targets, please remove from restrictions.bzl: " + ", ".join(no_longer_violations))

    for target, outside_project in dependencies.items():
        invalid = [dep for dep in outside_project if not _in_set(dep, allowed_external)]
        if invalid:
            tgts = ", ".join([str(t.label) for t in invalid])
            error = "Invalid dependencies for target " + str(target.label) + " [" + tgts + "]\n"
            error += "For more information see restrictions.bzl"
            fail(error)

    # Check allowed_external does not contain unnecesary targets
    current_allowed_external = {}
    for target, outside_project in dependencies.items():
        for out in outside_project:
            item = _in_set(out, allowed_external)
            if item:
                current_allowed_external[item] = item
    if sorted(current_allowed_external.keys()) != sorted(allowed_external):
        no_longer_needed = [e for e in allowed_external if e not in current_allowed_external]
        if no_longer_needed:
            tgts = ", ".join([str(t) for t in no_longer_needed])
            fail("The following external dependencies are no longer needed: " + tgts)

restricted_deps_aspect = aspect(
    implementation = _restricted_deps_aspect_impl,
    attr_aspects = ["*"],
)
