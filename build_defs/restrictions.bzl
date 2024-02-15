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

# List of targets that use internal only Guava APIs that need to be cleaned up.
# Targets in this list are java_library's that do not have the line:
# plugins = ["//java/com/google/devtools/build/buildjar/plugin/annotations:google_internal_checker"],
EXISTING_UNCHECKED = [
]

RestrictedInfo = provider(
    doc = "The dependencies, per target, outside the project",
    fields = {
        "dependencies": "A map from target to external dependencies",
        "unchecked": "A list of targets that are still unchecked for guava internal APIs",
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
    if hasattr(ctx.rule.attr, "runtime_deps"):
        deps.extend(ctx.rule.attr.runtime_deps)
    return deps

def _restricted_deps_aspect_impl(target, ctx):
    if not _in_project(target):
        return []

    unchecked = []
    if ctx.rule.kind == "java_library":
        if ctx.rule.attr.plugins:
            labels = [t.label for t in ctx.rule.attr.plugins]
            if (Label("//java/com/google/devtools/build/buildjar/plugin/annotations:google_internal_checker") not in labels):
                unchecked.append(target)
        else:
            unchecked.append(target)

    dependencies = {}
    nested_unchecked = []
    outside_project = []
    for d in _get_deps(ctx):
        if not _in_project(d) and not _in_set(d, _valid):
            outside_project.append(d)
        if RestrictedInfo in d:
            dependencies.update(d[RestrictedInfo].dependencies)
            nested_unchecked.append(d[RestrictedInfo].unchecked)

    if outside_project:
        dependencies[target] = outside_project

    return [RestrictedInfo(dependencies = dependencies, unchecked = depset(direct = unchecked, transitive = nested_unchecked))]

# buildifier: disable=function-docstring
def validate_unchecked_internal(unchecked, existing_unchecked):
    not_allowed_to_be_unchecked = [t for t in unchecked if t not in existing_unchecked]
    checked_still_in_list = [t for t in existing_unchecked if t not in unchecked]
    error = ""
    if not_allowed_to_be_unchecked:
        error += "The following targets do not have either google_internal_checker or beta_checker on:\n    " + "\n    ".join(not_allowed_to_be_unchecked) + "\n"
    if checked_still_in_list:
        error += "The following targets are checked but still in the EXISTING_UNCHECKED list:\n    " + "\n    ".join(checked_still_in_list) + "\n"
    if error:
        fail(error)

# buildifier: disable=function-docstring
def validate_restrictions(dependencies, allowed_external, existing_violations):
    violations = sorted([str(d.label) for d in dependencies.keys()])
    error = ""
    if violations != sorted(existing_violations):
        new_violations = [t for t in violations if t not in existing_violations]
        no_longer_violations = [t for t in existing_violations if t not in violations]
        if new_violations:
            error += "These targets now depend on external targets:\n    " + "\n    ".join(new_violations) + "\n"

        if no_longer_violations:
            error += "The following targets no longer depend on external targets, please remove from restrictions.bzl:\n    " + "\n    ".join(no_longer_violations) + "\n"

    for target, outside_project in dependencies.items():
        invalid = [dep for dep in outside_project if not _in_set(dep, allowed_external)]
        if invalid:
            tgts = [str(t.label) for t in invalid]
            error += "Invalid dependencies for target " + str(target.label) + "\n    " + "\n    ".join(tgts) + "\n"
    if error != "":
        error += "For more information see restrictions.bzl"
        fail(error)

    # Check allowed_external does not contain unnecessary targets
    current_allowed_external = {}
    for target, outside_project in dependencies.items():
        for out in outside_project:
            item = _in_set(out, allowed_external)
            if item:
                current_allowed_external[item] = item
    if sorted(current_allowed_external.keys()) != sorted(allowed_external):
        no_longer_needed = [e for e in allowed_external if e not in current_allowed_external]
        if no_longer_needed:
            tgts = [str(t) for t in no_longer_needed]
            fail("The following external dependencies are no longer needed: " + "\n    " + "\n    ".join(tgts) + "\n")

restricted_deps_aspect = aspect(
    implementation = _restricted_deps_aspect_impl,
    attr_aspects = ["*"],
)
