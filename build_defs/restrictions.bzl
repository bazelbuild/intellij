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

# Targets from the project scope that should be reported as external targets.
_not_project_for_tests = [
]

# A set of external dependencies that can be built outside of google3
_valid = [
]

EXTERNAL_DEPENDENCIES = {
}

# List of targets that use internal only Guava APIs that need to be cleaned up.
# Targets in this list are java_library's that do not have the line:
# plugins = ["//java/com/google/devtools/build/buildjar/plugin/annotations:google_internal_checker"],
EXISTING_UNCHECKED = [
]

# A temporary list of external targets that plugins are depending on. DO NOT ADD TO THIS
ALLOWED_EXTERNAL_TEST_DEPENDENCIES = [
    "//dart/build_defs/dart_library:dartinfo",
    "//devtools/blaze/integration:android_tools",
    "//devtools/blaze/integration:mobile_install",
    "//devtools/blaze/integration:mock_tools",
    "//devtools/blaze/main:blaze",
    "//devtools/citc/proto:citc_filesystem_manifest_java_proto",
    "//devtools/citc/proto:java_proto",
    "//devtools/deps/depserver/proto:dependency_service_java_proto",
    "//devtools/ide/intellij/filewatcher:regurgitator_client_mock_bin",
    "//devtools/ide/intellij/filewatcher:regurgitator_java_proto",
    "//devtools/srcfs/client/proto:delta_java_proto",
    "//google/corp/devtools/intellij/services/v1:dependency_service_java_grpc",
    "//java/com/google/common/flags:flags",
    "//java/com/google/common/hash:hash",
    "//java/com/google/experiments/framework/options:exempt_existing_client_java",
    "//java/com/google/testing/junit/junit4:api",
    "//java/com/google/testing/junit/runner:Runner_deploy.jar",
    "//java/com/google/testing/testsize:annotations",
    "//javascript/typescript:providers",
    "//third_party/java/apache_bcel:apache_bcel",
    "//third_party/java/flogger:flogger",
    "//third_party/java/truth/extensions:proto",
    "//tools/build_defs/js/providers:providers",
    "//tools/build_defs/js/providers:utils",
    "//tools/jdk:singlejar",
]

# A list of targets currently with not allowed dependencies
EXISTING_EXTERNAL_TEST_VIOLATIONS = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:blaze_integration_tests",
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:integration_tests",
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:unit_tests",
    "//javatests/com/google/devtools/intellij/blaze/plugin/base:utils",
    "//javatests/com/google/devtools/intellij/blaze/plugin/shell:blaze_integration_test_wrapper",
    "//javatests/com/google/devtools/intellij/g3plugins/citc/filewatcher:regurgitator_filewatcher_tests",
    "//javatests/com/google/devtools/intellij/g3plugins/integrity:integritycheck",
    "//javatests/com/google/devtools/intellij/g3plugins/services/depserver:unit_tests",
    "//javatests/com/google/devtools/intellij/g3plugins/services/linter:unit_tests",
]

RestrictedInfo = provider(
    doc = "The dependencies, per target, outside the project",
    fields = {
        "roots": "A depset of roots of all target trees that don't have external dependencies",
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

def _in_tests(target):
    lbl = str(target.label)
    return (lbl.endswith("_test") or lbl.endswith("_tests") or lbl.endswith(":tests")) and _in_project(target) or _in_set(target, _tests)

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

    unchecked = []
    if ctx.rule.kind == "java_library":
        if ctx.rule.attr.plugins:
            labels = [t.label for t in ctx.rule.attr.plugins]
            if (Label("//java/com/google/devtools/build/buildjar/plugin/annotations:google_internal_checker") not in labels):
                unchecked.append(target)
        else:
            unchecked.append(target)

    nested_roots = []
    dependencies = {}
    nested_unchecked = []
    outside_project = []
    for d in _get_deps(ctx):
        if not _in_project(d) and not _in_set(d, _valid):
            outside_project.append(d)
        if RestrictedInfo in d:
            dependencies.update(d[RestrictedInfo].dependencies)
            nested_unchecked.append(d[RestrictedInfo].unchecked)
            nested_roots.append(d[RestrictedInfo].roots)

    if outside_project:
        dependencies[target] = outside_project

    if dependencies:
        # This target cannot be a root as either itself or its dependencies depend on out of project targets
        roots = depset(direct = [], transitive = nested_roots)
    else:
        # No external dependencies on the entire subtree, we are a root
        roots = depset(direct = [target])

    return [RestrictedInfo(
        dependencies = dependencies,
        unchecked = depset(direct = unchecked, transitive = nested_unchecked),
        roots = roots,
    )]

# buildifier: disable=function-docstring
def validate_unchecked_internal(unchecked):
    not_allowed_to_be_unchecked = [t for t in unchecked if t not in EXISTING_UNCHECKED]
    checked_still_in_list = [t for t in EXISTING_UNCHECKED if t not in unchecked]
    error = ""
    if not_allowed_to_be_unchecked:
        error += "The following targets do not have either google_internal_checker or beta_checker on:\n    " + "\n    ".join(not_allowed_to_be_unchecked) + "\n"
    if checked_still_in_list:
        error += "The following targets are checked but still in the EXISTING_UNCHECKED list:\n    " + "\n    ".join(checked_still_in_list) + "\n"
    if error:
        fail(error)

def _restricted_test_deps_aspect_impl(target, ctx):
    if not _in_tests(target):
        return []

    dependencies = {}
    outside_project = []
    for d in _get_deps(ctx):
        if not _in_tests(d) and not _in_set(d, _valid) and (not _in_project(d) or _in_set(d, _not_project_for_tests)):
            outside_project.append(d)
        if RestrictedInfo in d:
            dependencies.update(d[RestrictedInfo].dependencies)

    if outside_project:
        dependencies[target] = outside_project

    return [
        RestrictedInfo(dependencies = dependencies),
    ]

def validate_restrictions(dependencies):
    external_dependencies = {str(k.label): [str(vt.label) for vt in v] for (k, v) in dependencies.items()}
    if external_dependencies != EXTERNAL_DEPENDENCIES:
        error = (
            "\nEXTERNAL_DEPENDENCIES = {\n    " +
            "\n    ".join(
                [
                    "\"" + str(t.label) + "\": [\n        " +
                    "        ".join(["\"" + str(vt.label) + "\",\n" for vt in v]) +
                    "    ],"
                    for (t, v) in dependencies.items()
                ],
            ) + "\n}\n"
        )
        fail(error)

# buildifier: disable=function-docstring
def _validate_test_restrictions(dependencies, allowed_external, existing_violations):
    violations = sorted([str(d.label) for d in dependencies.keys()])
    error = ""
    if violations != sorted(existing_violations):
        new_violations = [t for t in violations if t not in existing_violations]
        no_longer_violations = [t for t in existing_violations if t not in violations]
        if new_violations:
            error += (
                "These targets now depend on external targets:\n    " +
                "\n    ".join(
                    [
                        str(t.label) + " =>\n        " +
                        "\n        ".join([str(vt.label) for vt in v])
                        for (t, v) in dependencies.items()
                        if str(t.label) in new_violations
                    ],
                ) + "\n"
            )

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

restricted_test_deps_aspect = aspect(
    implementation = _restricted_test_deps_aspect_impl,
    attr_aspects = ["*"],
)

def _validate_test_dependencies(ctx):
    dependencies = {}
    for k in ctx.attr.deps:
        if not str(k.label).endswith("_tests") and not _in_tests(k):
            fail("Undeclared test location: " + str(k))
        if RestrictedInfo in k:
            dependencies.update(k[RestrictedInfo].dependencies)
    _validate_test_restrictions(dependencies, ctx.attr.allowed_external_dependencies, ctx.attr.existing_external_violations)
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
