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

_tests = [
]

_not_project_for_tests = [
    "//java/com/google/devtools/intellij/g3plugins:_internal_repackaged_repackaged_google3_deps",
    "//java/com/google/devtools/intellij/g3plugins:repackaged_google3_deps",
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

# A temporary list of external targets that plugins are depending on. DO NOT ADD TO THIS
ALLOWED_EXTERNAL_TEST_DEPENDENCIES = [
    "//java/com/google/devtools/build/runtime:runfiles",
    "//java/com/google/testing/junit/junit4:api",
    "//java/com/google/testing/junit/junit4:suites",
    "//java/com/google/testing/junit/junit4:testsize",
    "//java/com/google/testing/testsize:testsize",
    "//java/com/google/testing/testsize:annotations",
    "third_party/java/intellijprotobufplugin",
]

# A list of targets currently with not allowed dependencies
EXISTING_EXTERNAL_TEST_VIOLATIONS = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:blaze_integration_tests",
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:integration_tests",
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:unit_tests",
    "//javatests/com/google/devtools/intellij/blaze/plugin/base:utils",
    "//javatests/com/google/devtools/intellij/blaze/plugin/shell:blaze_integration_test_wrapper",
    "//javatests/com/google/devtools/intellij/g3plugins/citc/filewatcher:regurgitator_filewatcher_tests",
    "//javatests/com/google/devtools/intellij/g3plugins/formatter:integration_tests",
    "//javatests/com/google/devtools/intellij/g3plugins/integrity:integritycheck",
    "//javatests/com/google/devtools/intellij/g3plugins/services/depserver:unit_tests",
    "//javatests/com/google/devtools/intellij/g3plugins/services/linter:unit_tests",
    "//javatests/com/google/devtools/intellij/g3plugins:common_ext_tests",
    "//javatests/com/google/devtools/intellij/g3plugins:intellij_tests",
    #    "//third_party/java_src/intellijperforceplugin/testSource:tests_lib",
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

def _in_tests(target):
    l = str(target.label)
    return (l.endswith("_test") or l.endswith("_tests") or l.endswith(":tests")) and _in_project(target) or _in_set(target, _tests)

def _get_deps(ctx):
    deps = []
    if hasattr(ctx.rule.attr, "deps"):
        deps.extend(ctx.rule.attr.deps)
    if hasattr(ctx.rule.attr, "exports"):
        deps.extend(ctx.rule.attr.exports)
    if hasattr(ctx.rule.attr, "runtime_deps"):
        deps.extend(ctx.rule.attr.runtime_deps)
    if hasattr(ctx.rule.attr, "data"):
        deps.extend(ctx.rule.attr.data)
    if hasattr(ctx.rule.attr, "tests"):
        deps.extend(ctx.rule.attr.tests)
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

def _restricted_test_deps_aspect_impl(target, ctx):
    if not _in_tests(target):
        return []

    # print("C: " + str(target.label))
    dependencies = {}
    outside_project = []
    for d in _get_deps(ctx):
        if not _in_tests(d) and not _in_set(d, _valid) and (not _in_project(d) or _in_set(d, _not_project_for_tests)):
            outside_project.append(d)
        if RestrictedInfo in d:
            # if d[RestrictedInfo].dependencies:
            #     print("F: " + str(d.label))
            dependencies.update(d[RestrictedInfo].dependencies)

    if outside_project:
        dependencies[target] = outside_project

    if str(target.label).endswith("_test") and len(dependencies.keys()) == 0:
        print("OK: " + str(target.label))

    #        print("FOUND: " + str(target) + " => " + str(outside_project))
    return [
        RestrictedInfo(dependencies = dependencies),
    ]

# buildifier: disable=function-docstring
def validate_restrictions(dependencies, allowed_external, existing_violations, strict = True):
    violations = sorted([str(d.label) for d in dependencies.keys()])

    #    print("???????????????????????????????" + str(dependencies))
    error = ""
    if violations != sorted(existing_violations):
        new_violations = [t for t in violations if t not in existing_violations]
        no_longer_violations = [t for t in existing_violations if t not in violations]
        if new_violations:
            error += "These targets now depend on external targets:\n    " + "\n    ".join(new_violations) + "\n"

        if strict and no_longer_violations:
            error += "The following targets no longer depend on external targets, please remove from restrictions.bzl: " + ", ".join(no_longer_violations)

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
        if strict and no_longer_needed:
            tgts = [str(t) for t in no_longer_needed]
            fail("The following external dependencies are no longer needed: " + "\n    " + "\n    ".join(tgts) + "\n")

restricted_deps_aspect = aspect(
    implementation = _restricted_deps_aspect_impl,
    attr_aspects = ["*"],
)

restricted_test_deps_aspect = aspect(
    implementation = _restricted_test_deps_aspect_impl,
    attr_aspects = ["*"],
)

def _validate_test_dependencies(ctx):
    dependencies = {}
    print("VAL: " + str(ctx.attr.deps))
    for k in ctx.attr.deps:
        print("T : " + str(k.label))
        if not str(k.label).endswith("_tests") and not _in_tests(k):
            fail("Undeclared test location: " + str(k))
        if RestrictedInfo in k:
            dependencies.update(k[RestrictedInfo].dependencies)
    validate_restrictions(dependencies, ctx.attr.allowed_external_dependencies, ctx.attr.existing_external_violations)
    fake_file = ctx.actions.declare_file("fake_file.txt")
    ctx.actions.write(
        fake_file,
        """#!/bin/sh

        true
        """,
    )
    return [DefaultInfo(executable = fake_file)]

validate_test_dependencies_test = rule(
    implementation = _validate_test_dependencies,
    attrs = {
        "allowed_external_dependencies": attr.string_list(),
        "existing_external_violations": attr.string_list(),
        "deps": attr.label_list(aspects = [restricted_test_deps_aspect]),
    },
    test = True,
)
