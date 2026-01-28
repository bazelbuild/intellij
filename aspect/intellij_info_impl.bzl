"""Implementation of IntelliJ-specific information collecting aspect."""

load(
    "@bazel_tools//tools/build_defs/cc:action_names.bzl",
    "ACTION_NAMES",
)
load(
    ":artifacts.bzl",
    "artifact_location",
    "artifacts_from_target_list_attr",
    "is_external_artifact",
    "sources_from_target",
    "struct_omit_none",
    "to_artifact_location",
)
load(":cc_info.bzl", "CC_USE_GET_TOOL_FOR_ACTION", "CcInfoCompat", "cc_common_compat")
load(":code_generator_info.bzl", "CODE_GENERATOR_RULE_NAMES")
load(
    ":make_variables.bzl",
    "expand_make_variables",
)
load(":python_info.bzl", "get_py_info", "py_info_in_target")

IntelliJInfo = provider(
    doc = "Collected information about the targets visited by the aspect.",
    fields = [
        "kind",
        "output_groups",
        "target_key",
    ],
)

# Defensive list of features that can appear in the C++ toolchain, but which we
# definitely don't want to enable (when enabled, they'd contribute command line
# flags that don't make sense in the context of intellij info).
UNSUPPORTED_FEATURES = [
    "thin_lto",
    "module_maps",
    "use_header_modules",
    "fdo_instrument",
    "fdo_optimize",
]

# Compile-time dependency attributes, grouped by type.
DEPS = [
    "_stl",  # From cc rules
    "malloc",  # From cc_binary rules
    "implementation_deps",  # From cc_library rules
    "_java_toolchain",  # From java rules
    "deps",
    "jars",  # from java_import rules
    "exports",
    "java_lib",  # From old proto_library rules
    "_android_sdk",  # from android rules
    "aidl_lib",  # from android_sdk
    "_scala_toolchain",  # From scala rules
    "test_app",  # android_instrumentation_test
    "instruments",  # android_instrumentation_test
    "tests",  # From test_suite
    "compilers",  # From go_proto_library
    "associates",  # From kotlin rules
]

# Run-time dependency attributes, grouped by type.
RUNTIME_DEPS = [
    "runtime_deps",
]

PREREQUISITE_DEPS = []

# Dependency type enum
COMPILE_TIME = 0

RUNTIME = 1

# PythonVersion enum; must match PyIdeInfo.PythonVersion
PY2 = 1

PY3 = 2

# PythonCompatVersion enum; must match PyIdeInfo.PythonSrcsVersion
SRC_PY2 = 1

SRC_PY3 = 2

SRC_PY2AND3 = 3

SRC_PY2ONLY = 4

SRC_PY3ONLY = 5

##### Helpers

def get_code_generator_rule_names(ctx, language_name):
    """Supplies a list of Rule names for code generation for the language specified

    For some languages, it is possible to specify Rules' names that are interpreted as
    code-generators for the language. These Rules' names are specified as attrs and are provided to
    the aspect using the `AspectStrategy#AspectParameter` in the plugin logic.
    """

    if not language_name:
        fail("the `language_name` must be provided")

    if hasattr(CODE_GENERATOR_RULE_NAMES, language_name):
        return getattr(CODE_GENERATOR_RULE_NAMES, language_name)

    return []

def build_file_artifact_location(ctx):
    """Creates an ArtifactLocation proto representing a location of a given BUILD file."""
    return to_artifact_location(
        ctx.label.workspace_root,
        ctx.label.package + "/BUILD",
        True,
        is_external_artifact(ctx.label),
    )

def _collect_target_from_attr(rule_attrs, attr_name, result):
    """Collects the targets from the given attr into the result."""
    if not hasattr(rule_attrs, attr_name):
        return
    attr_value = getattr(rule_attrs, attr_name)
    type_name = type(attr_value)
    if type_name == "Target":
        result.append(attr_value)
    elif type_name == "list":
        result.extend(attr_value)

def collect_targets_from_attrs(rule_attrs, attrs):
    """Returns a list of targets from the given attributes."""
    result = []
    for attr_name in attrs:
        _collect_target_from_attr(rule_attrs, attr_name, result)
    return [target for target in result if is_valid_aspect_target(target)]

def is_valid_aspect_target(target):
    """Returns whether the target has had the aspect run on it."""
    return IntelliJInfo in target

def get_aspect_ids(ctx):
    """Returns the all aspect ids, filtering out self."""
    aspect_ids = None
    if hasattr(ctx, "aspect_ids"):
        aspect_ids = ctx.aspect_ids
    else:
        return None
    return [aspect_id for aspect_id in aspect_ids if "intellij_info_aspect" not in aspect_id]

def _is_language_specific_proto_library(ctx, target, semantics):
    """Returns True if the target is a proto library with attached language-specific aspect."""
    if ctx.rule.kind != "proto_library":
        return False
    if CcInfoCompat in target:
        return True
    return False

def stringify_label(label):
    """Stringifies a label, making sure any leading '@'s are stripped from main repo labels."""
    s = str(label)

    # If the label is in the main repo, make sure any leading '@'s are stripped so that tests are
    # okay with the fixture setups.
    return s.lstrip("@") if s.startswith("@@//") or s.startswith("@//") else s

def make_target_key(label, aspect_ids):
    """Returns a TargetKey proto struct from a target."""
    return struct_omit_none(
        aspect_ids = tuple(aspect_ids) if aspect_ids else None,
        label = stringify_label(label),
    )

def make_dep(dep, dependency_type):
    """Returns a Dependency proto struct."""
    return struct(
        dependency_type = dependency_type,
        target = dep[IntelliJInfo].target_key,
    )

def make_deps(deps, dependency_type):
    """Returns a list of Dependency proto structs."""
    return [make_dep(dep, dependency_type) for dep in deps]

def update_sync_output_groups(groups_dict, key, new_set):
    """Updates all sync-relevant output groups associated with 'key'.

    This is currently the [key] output group itself, together with [key]-outputs
    and [key]-direct-deps.

    Args:
      groups_dict: the output groups dict, from group name to artifact depset.
      key: the base output group name.
      new_set: a depset of artifacts to add to the output groups.
    """
    update_set_in_dict(groups_dict, key, new_set)
    update_set_in_dict(groups_dict, key + "-outputs", new_set)
    update_set_in_dict(groups_dict, key + "-direct-deps", new_set)

def update_set_in_dict(input_dict, key, other_set):
    """Updates depset in dict, merging it with another depset."""
    input_dict[key] = depset(transitive = [input_dict.get(key, depset()), other_set])

def _get_output_mnemonic(ctx):
    """Gives the output directory mnemonic for some target context."""
    return ctx.bin_dir.path.split("/")[1]

def _get_python_version(ctx):
    return PY3

_SRCS_VERSION_MAPPING = {
    "PY2": SRC_PY2,
    "PY3": SRC_PY3,
    "PY2AND3": SRC_PY2AND3,
    "PY2ONLY": SRC_PY2ONLY,
    "PY3ONLY": SRC_PY3ONLY,
}

def _get_python_srcs_version(ctx):
    srcs_version = getattr(ctx.rule.attr, "srcs_version", "PY2AND3")
    return _SRCS_VERSION_MAPPING.get(srcs_version, default = SRC_PY2AND3)

##### Builders for individual parts of the aspect output

def collect_py_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates Python-specific output groups, returns false if not a Python target."""
    if not py_info_in_target(target) or _is_language_specific_proto_library(ctx, target, semantics):
        return False

    py_semantics = getattr(semantics, "py", None)
    if py_semantics:
        py_launcher = py_semantics.get_launcher(target, ctx)
    else:
        py_launcher = None

    sources = sources_from_target(ctx)
    to_build = get_py_info(target).transitive_sources
    args = getattr(ctx.rule.attr, "args", [])
    data_deps = getattr(ctx.rule.attr, "data", [])
    args = expand_make_variables(ctx, False, args)
    imports = getattr(ctx.rule.attr, "imports", [])
    is_code_generator = False

    # If there are apparently no sources found from `srcs` and the target has a rule name which is
    # one of the ones pre-specified to the aspect as being a code-generator for Python then
    # interpret the outputs of the target specified in the PyInfo as being sources.

    if 0 == len(sources) and ctx.rule.kind in get_code_generator_rule_names(ctx, "python"):
        def provider_import_to_attr_import(provider_import):
            """\
            Remaps the imports from PyInfo

            The imports that are supplied on the `PyInfo` are relative to the runfiles and so are
            not the same as those which might be supplied on an attribute of `py_library`. This
            function will remap those back so they look as if they were `imports` attributes on
            the rule. The form of the runfiles import is `<workspace_name>/<package_dir>/<import>`.
            The actual `workspace_name` is not interesting such that the first part can be simply
            stripped. Next the package to the Label is stripped leaving a path that would have been
            supplied on an `imports` attribute to a Rule.
            """

            # Other code in this file appears to assume *NIX path component separators?

            provider_import_parts = [p for p in provider_import.split("/")]
            package_parts = [p for p in ctx.label.package.split("/")]

            if 0 == len(provider_import_parts):
                return None

            scratch_parts = provider_import_parts[1:]  # remove the workspace name or _main

            for p in package_parts:
                if 0 != len(provider_import_parts) and scratch_parts[0] == p:
                    scratch_parts = scratch_parts[1:]
                else:
                    return None

            return "/".join(scratch_parts)

        def provider_imports_to_attr_imports():
            result = []

            for provider_import in get_py_info(target).imports.to_list():
                attr_import = provider_import_to_attr_import(provider_import)
                if attr_import:
                    result.append(attr_import)

            return result

        if get_py_info(target).imports:
            imports.extend(provider_imports_to_attr_imports())

        runfiles = target[DefaultInfo].default_runfiles

        if runfiles and runfiles.files:
            sources.extend([artifact_location(f) for f in runfiles.files.to_list()])

        is_code_generator = True

    ide_info["py_ide_info"] = struct_omit_none(
        launcher = py_launcher,
        python_version = _get_python_version(ctx),
        sources = sources,
        srcs_version = _get_python_srcs_version(ctx),
        args = args,
        imports = imports,
        is_code_generator = is_code_generator,
    )

    update_sync_output_groups(output_groups, "intellij-info-py", depset([ide_info_file]))
    update_sync_output_groups(output_groups, "intellij-compile-py", to_build)
    update_sync_output_groups(output_groups, "intellij-resolve-py", to_build)
    return True

def collect_cc_rule_context(ctx):
    """Collect additional information from the rule attributes of cc_xxx rules."""

    if not ctx.rule.kind.startswith("cc_"):
        return struct()

    return struct(
        sources = artifacts_from_target_list_attr(ctx, "srcs"),
        headers = artifacts_from_target_list_attr(ctx, "hdrs"),
        textual_headers = artifacts_from_target_list_attr(ctx, "textual_hdrs"),
        copts = expand_make_variables(ctx, True, getattr(ctx.rule.attr, "copts", [])),
        conlyopts = expand_make_variables(ctx, True, getattr(ctx.rule.attr, "conlyopts", [])),
        cxxopts = expand_make_variables(ctx, True, getattr(ctx.rule.attr, "cxxopts", [])),
        args = expand_make_variables(ctx, True, getattr(ctx.rule.attr, "args", [])),
        include_prefix = getattr(ctx.rule.attr, "include_prefix", ""),
        strip_include_prefix = getattr(ctx.rule.attr, "strip_include_prefix", ""),
    )

def collect_cc_compilation_context(ctx, target):
    """Collect information from the compilation context provided by the CcInfoCompat provider."""

    compilation_context = target[CcInfoCompat].compilation_context

    # collect non-propagated attributes before potentially merging with implementation deps
    local_defines = compilation_context.local_defines.to_list()

    # merge current compilation context with context of implementation dependencies
    if ctx.rule.kind.startswith("cc_") and hasattr(ctx.rule.attr, "implementation_deps"):
        impl_deps = ctx.rule.attr.implementation_deps

        compilation_context = cc_common_compat.merge_compilation_contexts(
            compilation_contexts = [compilation_context] + [it[CcInfoCompat].compilation_context for it in impl_deps],
        )

    # external_includes available since bazel 7
    external_includes = getattr(compilation_context, "external_includes", depset()).to_list()

    return struct(
        headers = [artifact_location(it) for it in compilation_context.headers.to_list()],
        defines = compilation_context.defines.to_list() + local_defines,
        includes = compilation_context.includes.to_list(),
        quote_includes = compilation_context.quote_includes.to_list(),
        # both system and external includes are added using `-isystem`
        system_includes = compilation_context.system_includes.to_list() + external_includes,
    )

def collect_cpp_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates C++-specific output groups, returns false if not a C++ target."""

    if CcInfoCompat not in target:
        return False

    # ignore cc_proto_library, attach to proto_library with aspect attached instead
    if ctx.rule.kind == "cc_proto_library":
        return False

    # Go targets always provide CcInfoCompat. Usually it's empty, but even if it isn't we don't handle it
    if ctx.rule.kind.startswith("go_"):
        return False

    ide_info["c_ide_info"] = struct(
        rule_context = collect_cc_rule_context(ctx),
        compilation_context = collect_cc_compilation_context(ctx, target),
    )
    resolve_files = target[CcInfoCompat].compilation_context.headers

    # TODO(brendandouglas): target to cpp files only
    compile_files = target[OutputGroupInfo].compilation_outputs if hasattr(target[OutputGroupInfo], "compilation_outputs") else depset([])

    update_sync_output_groups(output_groups, "intellij-info-cpp", depset([ide_info_file]))
    update_sync_output_groups(output_groups, "intellij-compile-cpp", compile_files)
    update_sync_output_groups(output_groups, "intellij-resolve-cpp", resolve_files)
    return True

def collect_c_toolchain_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates cc_toolchain-relevant output groups, returns false if not a cc_toolchain target."""

    # The other toolchains like the JDK might also have ToolchainInfo but it's not a C++ toolchain,
    # so check kind as well.
    # TODO(jvoung): We are temporarily getting info from cc_toolchain_suite
    # https://github.com/bazelbuild/bazel/commit/3aedb2f6de80630f88ffb6b60795c44e351a5810
    # but will switch back to cc_toolchain providing CcToolchainProvider once we migrate C++ rules
    # to generic platforms and toolchains.
    if ctx.rule.kind != "cc_toolchain" and ctx.rule.kind != "cc_toolchain_suite" and ctx.rule.kind != "cc_toolchain_alias":
        return False
    if cc_common_compat.CcToolchainInfo not in target:
        return False

    # cc toolchain to access compiler flags
    cpp_toolchain = target[cc_common_compat.CcToolchainInfo]

    # cpp fragment to access bazel options
    cpp_fragment = ctx.fragments.cpp

    copts = cpp_fragment.copts
    cxxopts = cpp_fragment.cxxopts
    conlyopts = cpp_fragment.conlyopts

    feature_configuration = cc_common_compat.configure_features(
        ctx = ctx,
        cc_toolchain = cpp_toolchain,
        requested_features = ctx.features,
        unsupported_features = ctx.disabled_features + UNSUPPORTED_FEATURES,
    )
    c_variables = cc_common_compat.create_compile_variables(
        feature_configuration = feature_configuration,
        cc_toolchain = cpp_toolchain,
        user_compile_flags = copts + conlyopts,
    )
    cpp_variables = cc_common_compat.create_compile_variables(
        feature_configuration = feature_configuration,
        cc_toolchain = cpp_toolchain,
        user_compile_flags = copts + cxxopts,
    )
    c_options = cc_common_compat.get_memory_inefficient_command_line(
        feature_configuration = feature_configuration,
        action_name = ACTION_NAMES.c_compile,
        variables = c_variables,
    )
    cpp_options = cc_common_compat.get_memory_inefficient_command_line(
        feature_configuration = feature_configuration,
        action_name = ACTION_NAMES.cpp_compile,
        variables = cpp_variables,
    )

    if CC_USE_GET_TOOL_FOR_ACTION:
        c_compiler = cc_common_compat.get_tool_for_action(
            feature_configuration = feature_configuration,
            action_name = ACTION_NAMES.c_compile,
        )
        cpp_compiler = cc_common_compat.get_tool_for_action(
            feature_configuration = feature_configuration,
            action_name = ACTION_NAMES.cpp_compile,
        )
    else:
        c_compiler = str(cpp_toolchain.compiler_executable)
        cpp_compiler = str(cpp_toolchain.compiler_executable)

    c_toolchain_info = struct_omit_none(
        built_in_include_directory = [str(d) for d in cpp_toolchain.built_in_include_directories],
        c_option = c_options,
        cpp_option = cpp_options,
        c_compiler = c_compiler,
        cpp_compiler = cpp_compiler,
        target_name = cpp_toolchain.target_gnu_system_name,
        compiler_name = cpp_toolchain.compiler,
        sysroot = cpp_toolchain.sysroot,
    )
    ide_info["c_toolchain_ide_info"] = c_toolchain_info
    update_sync_output_groups(output_groups, "intellij-info-cpp", depset([ide_info_file]))
    return True

def build_test_info(ctx):
    """Build TestInfo."""
    if not is_test_rule(ctx):
        return None
    return struct_omit_none(
        size = ctx.rule.attr.size,
    )

def is_test_rule(ctx):
    kind_string = ctx.rule.kind
    return kind_string.endswith("_test")

def _is_proto_library_wrapper(target, ctx):
    """Returns True if the target is an empty shim around a proto library."""
    if not ctx.rule.kind.endswith("proto_library") or ctx.rule.kind == "proto_library":
        return False

    # treat any *proto_library rule with a single proto_library dep as a shim
    deps = collect_targets_from_attrs(ctx.rule.attr, ["deps"])
    return len(deps) == 1 and IntelliJInfo in deps[0] and deps[0][IntelliJInfo].kind == "proto_library"

def _get_forwarded_deps(target, ctx):
    """Returns the list of deps of this target to forward.

    Used to handle wrapper/shim targets which are really just pointers to a
    different target (for example, java_proto_library)
    """
    if _is_proto_library_wrapper(target, ctx):
        return collect_targets_from_attrs(ctx.rule.attr, ["deps"])
    return []

def _is_analysis_test(target):
    """Returns if the target is an analysis test.

    Rules created with analysis_test=True cannot create write actions, so the
    aspect should skip them.
    """
    return AnalysisTestResultInfo in target

##### Main aspect function

def intellij_info_aspect_impl(target, ctx, semantics):
    """Aspect implementation function."""

    tags = ctx.rule.attr.tags
    if "no-ide" in tags:
        return []

    if _is_analysis_test(target):
        return []

    rule_attrs = ctx.rule.attr

    # Collect direct dependencies
    direct_dep_targets = collect_targets_from_attrs(
        rule_attrs,
        semantics_extra_deps(DEPS, semantics, "extra_deps"),
    )

    # Collect direct toolchain type-based dependencies
    if hasattr(semantics, "toolchains_propagation"):
        direct_dep_targets.extend(
            semantics.toolchains_propagation.collect_toolchain_deps(
                ctx,
                semantics.toolchains_propagation.toolchain_types,
            ),
        )

    compiletime_deps = make_deps(direct_dep_targets, COMPILE_TIME)

    # runtime_deps
    runtime_dep_targets = collect_targets_from_attrs(
        rule_attrs,
        RUNTIME_DEPS,
    )
    runtime_deps = make_deps(runtime_dep_targets, RUNTIME)
    all_deps = depset(compiletime_deps + runtime_deps).to_list()

    # extra prerequisites
    extra_prerequisite_targets = collect_targets_from_attrs(
        rule_attrs,
        semantics_extra_deps(PREREQUISITE_DEPS, semantics, "extra_prerequisites"),
    )

    forwarded_deps = _get_forwarded_deps(target, ctx)

    # Roll up output files from my prerequisites
    prerequisites = direct_dep_targets + runtime_dep_targets + extra_prerequisite_targets
    output_groups = dict()
    for dep in prerequisites:
        for k, v in dep[IntelliJInfo].output_groups.items():
            if dep in forwarded_deps:
                # unconditionally roll up deps for these targets
                output_groups[k] = output_groups[k] + [v] if k in output_groups else [v]
                continue

            # roll up outputs of direct deps into '-direct-deps' output group
            if k.endswith("-direct-deps"):
                continue
            if k.endswith("-outputs"):
                directs = k[:-len("outputs")] + "direct-deps"
                output_groups[directs] = output_groups[directs] + [v] if directs in output_groups else [v]
                continue

            # everything else gets rolled up transitively
            output_groups[k] = output_groups[k] + [v] if k in output_groups else [v]

    # Convert output_groups from lists to depsets after the lists are finalized. This avoids
    # creating and growing depsets gradually, as that results in depsets many levels deep:
    # a construct which would give the build system some trouble.
    for k, v in output_groups.items():
        output_groups[k] = depset(transitive = output_groups[k])

    # Initialize the ide info dict, and corresponding output file
    # This will be passed to each language-specific handler to fill in as required
    file_name = target.label.name

    # bazel allows target names differing only by case, so append a hash to support
    # case-insensitive file systems
    file_name = file_name + "-" + str(hash(file_name))
    aspect_ids = get_aspect_ids(ctx)
    if aspect_ids:
        aspect_hash = hash(".".join(aspect_ids))
        file_name = file_name + "-" + str(aspect_hash)
    file_name = file_name + ".intellij-info.txt"
    ide_info_file = ctx.actions.declare_file(file_name)

    target_key = make_target_key(target.label, aspect_ids)
    ide_info = dict(
        build_file_artifact_location = build_file_artifact_location(ctx),
        features = ctx.features,
        key = target_key,
        kind_string = ctx.rule.kind,
        tags = tags,
        deps = list(all_deps),
    )

    # Collect test info
    ide_info["test_info"] = build_test_info(ctx)

    handled = False
    handled = collect_py_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled
    handled = collect_cpp_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled
    handled = collect_c_toolchain_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled

    # Any extra ide info
    if hasattr(semantics, "extra_ide_info"):
        handled = semantics.extra_ide_info(target, ctx, ide_info, ide_info_file, output_groups) or handled

    # Add to generic output group if it's not handled by a language-specific handler
    if not handled:
        update_sync_output_groups(output_groups, "intellij-info-generic", depset([ide_info_file]))

    # Output the ide information file.
    info = struct_omit_none(**ide_info)
    ctx.actions.write(ide_info_file, proto.encode_text(info))

    # Return providers.
    return [
        IntelliJInfo(
            kind = ctx.rule.kind,
            output_groups = output_groups,
            target_key = target_key,
        ),
        OutputGroupInfo(**output_groups),
    ]

def semantics_extra_deps(base, semantics, name):
    if not hasattr(semantics, name):
        return base
    extra_deps = getattr(semantics, name)
    return base + extra_deps

def make_intellij_info_aspect(aspect_impl, semantics, **kwargs):
    """Creates the aspect given the semantics."""
    deps = semantics_extra_deps(DEPS, semantics, "extra_deps")
    runtime_deps = RUNTIME_DEPS
    prerequisite_deps = semantics_extra_deps(PREREQUISITE_DEPS, semantics, "extra_prerequisites")

    attr_aspects = deps + runtime_deps + prerequisite_deps

    attrs = {}

    # add attrs required by semantics
    if hasattr(semantics, "attrs"):
        attrs.update(semantics.attrs)

    return aspect(
        attr_aspects = attr_aspects,
        attrs = attrs,
        fragments = ["cpp"],
        required_aspect_providers = [[CcInfoCompat]] + semantics.extra_required_aspect_providers,
        implementation = aspect_impl,
        **kwargs
    )
