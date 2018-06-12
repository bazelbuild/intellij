"""An aspect to gather info needed by the FastBuildService."""

load(
    ":artifacts.bzl",
    "artifact_location",
    "sources_from_target",
    "struct_omit_none",
)

_DEP_ATTRS = ["deps", "exports", "runtime_deps", "_java_toolchain"]

def _fast_build_info_impl(target, ctx):
    dep_targets = _get_all_dep_targets(target, ctx)
    dep_outputs = _get_all_dep_outputs(dep_targets)

    output_files = []

    info = {
        "workspace_name": ctx.workspace_name,
        "label": str(target.label),
        "dependencies": [str(t.label) for t in dep_targets],
    }

    write_output = False
    if hasattr(target, "java_toolchain"):
        write_output = True
        toolchain = target.java_toolchain
        info["java_toolchain_info"] = struct_omit_none(
            javac_jar = artifact_location(toolchain.javac_jar),
            source_version = toolchain.source_version,
            target_version = toolchain.target_version,
        )
    if JavaInfo in target:
        write_output = True
        java_info = {
            "sources": sources_from_target(ctx),
            "test_class": getattr(ctx.rule.attr, "test_class", None),
        }
        annotation_processing = target[JavaInfo].annotation_processing
        if annotation_processing:
            java_info["annotation_processor_class_names"] = annotation_processing.processor_classnames
            java_info["annotation_processor_classpath"] = [
                artifact_location(t)
                for t in annotation_processing.processor_classpath
            ]
        info["java_info"] = struct_omit_none(**java_info)
    if hasattr(target, "android"):
        write_output = True
        android_info = struct_omit_none(
            aar = artifact_location(target.android.aar),
            merged_manifest = artifact_location(target.android.merged_manifest),
        )
        info["android_info"] = android_info

    if write_output:
        output_file = ctx.actions.declare_file(target.label.name + ".ide-fast-build-info.txt")
        ctx.actions.write(output_file, struct_omit_none(**info).to_proto())
        output_files += [output_file]

    output_groups = depset(output_files, transitive = dep_outputs)

    return struct(output_groups = {"ide-fast-build": output_groups})

def _get_all_dep_outputs(dep_targets):
    """Get the ide-fast-build output files for all dependencies"""
    return [
        dep_target[OutputGroupInfo]["ide-fast-build"]
        for dep_target in dep_targets
        if _has_ide_fast_build(dep_target)
    ]

def _get_all_dep_targets(target, ctx):
    """Get all the targets mentioned in one of the _DEP_ATTRS attributes of the target"""
    targets = []
    for attr_name in _DEP_ATTRS:
        attr_val = getattr(ctx.rule.attr, attr_name, default = None)
        if not attr_val:
            continue
        attr_type = type(attr_val)
        if attr_type == type(target):
            targets.append(attr_val)
        elif attr_type == type([]):
            targets += [list_val for list_val in attr_val if type(list_val) == type(target)]
    return targets

def _has_ide_fast_build(target):
    return OutputGroupInfo in target and "ide-fast-build" in target[OutputGroupInfo]

fast_build_info_aspect = aspect(
    attr_aspects = _DEP_ATTRS,
    implementation = _fast_build_info_impl,
)
