"""Rules for writing tests for the IntelliJ aspect."""

load(
    "//aspect:build_dependencies_blaze_deps.bzl",
    _ide_cc_not_validated = "IDE_CC",
    _ide_java_not_validated = "IDE_JAVA",
    _ide_java_proto_not_validated = "IDE_JAVA_PROTO",
    _ide_kotlin_not_validated = "IDE_KOTLIN",
)

def _rule_function(rule):  # @unused
    return []

def _target_rule_function(
        target,  # @unused
        rule):  # @unused
    return []

def _validate_ide(unvalidated, template):
    "Basic validation that a provided implementation conforms to a given template"
    for a in dir(template):
        if not hasattr(unvalidated, a):
            fail("attribute missing: ", a, unvalidated)
        elif type(getattr(unvalidated, a)) != type(getattr(template, a)):
            fail("attribute type mismatch: ", a, type(getattr(unvalidated, a)), type(getattr(template, a)))
    return struct(**{a: getattr(unvalidated, a) for a in dir(template) if a not in dir(struct())})

IDE_JAVA = _validate_ide(
    _ide_java_not_validated,
    template = struct(
        srcs_attributes = [],  # Additional srcs like attributes.
        get_java_info = _target_rule_function,  # A function that takes a rule and returns a JavaInfo like structure (or the provider itself).
    ),
)

IDE_KOTLIN = _validate_ide(
    _ide_kotlin_not_validated,
    template = struct(
        srcs_attributes = [],  # Additional srcs like attributes.
        follow_attributes = [],  # Additional attributes for the aspect to follow and request DependenciesInfo provider.
        follow_additional_attributes = [],  # Additional attributes for the aspect to follow without requesting DependenciesInfo provider.
        followed_dependencies = _rule_function,  # A function that takes a rule and returns a list of dependencies (targets or toolchain containers).
        toolchains_aspects = [],  # Toolchain types for the aspect to follow.
        get_kotlin_info = _target_rule_function,  # A function that takes a rule and returns a marker struct if the target
        # was recognised as a Kotlin related target and `followed_dependenices` must be called.
    ),
)

IDE_JAVA_PROTO = _validate_ide(
    _ide_java_proto_not_validated,
    template = struct(
        get_java_proto_info = _target_rule_function,  # A function that takes a rule and returns a marker structure (empty for now).
        srcs_attributes = [],  # Additional srcs like attributes.
        follow_attributes = [],  # Additional attributes for the aspect to follow and request DependenciesInfo provider.
        followed_dependencies = _rule_function,  # A function that takes a rule and returns a list of dependencies (targets or toolchain containers).
        toolchains_aspects = [],  # Toolchain types for the aspect to follow.
    ),
)

IDE_CC = _validate_ide(
    _ide_cc_not_validated,
    template = struct(
        c_compile_action_name = "",  # An action named to be used with cc_common.get_memory_inefficient_command_line or similar.
        cpp_compile_action_name = "",  # An action named to be used with cc_common.get_memory_inefficient_command_line or similar.
        follow_attributes = ["_cc_toolchain"],  # Additional attributes for the aspect to follow and request DependenciesInfo provider.
        toolchains_aspects = [],  # Toolchain types for the aspect to follow.
        toolchain_target = _rule_function,  # A function that takes a rule and returns a toolchain target (or a toolchain container).
    ),
)

# an aspect that will return the all the information that aswb needed of a target. If the information is not applied to that that target, it will return None.
def _aspect_impl(target, ctx):
    java_info = IDE_JAVA.get_java_info(target, ctx.rule)
    kotlin_info = IDE_KOTLIN.get_kotlin_info(target, ctx.rule)
    java_proto_info = IDE_JAVA_PROTO.get_java_proto_info(target, ctx.rule)
    cc_toolchain_target = IDE_CC.toolchain_target(ctx.rule)
    return struct(
        label = target.label,
        java_info = java_info,
        kotlin_info = kotlin_info,
        java_proto_info = java_proto_info,
        cc_toolchain_target = cc_toolchain_target,
        deps = ctx.rule.attr.deps,  # it can be usedfor propagating depedency information, but not used for now
    )

build_dependencies_blaze_deps_aspect = aspect(
    implementation = _aspect_impl,
    attr_aspects = ["deps"],
)

TargetInfo = provider("The language sepecific information for a target. When that lang_info is not applied to the target, it will be None.", fields = ["label", "java_info", "kotlin_info", "java_proto_info", "cc_toolchain_target"])

TargetsInfo = provider("A list of TargetInfo for all the targets in the dependency tree.", fields = ["target_infos"])

def _impl(ctx):
    """Returns a list of TargetInfo for the test target only (not its dependencies) ."""
    return TargetsInfo(
        target_infos = [
            TargetInfo(
                label = dep.label,
                java_info = dep.java_info,
                kotlin_info = dep.kotlin_info,
                java_proto_info = dep.java_proto_info,
                cc_toolchain_target = dep.cc_toolchain_target,
            )
            for dep in ctx.attr.deps
        ],
    )

build_dependencies_blaze_deps_test_fixture = rule(
    _impl,
    attrs = {
        "deps": attr.label_list(aspects = [build_dependencies_blaze_deps_aspect]),
    },
)
