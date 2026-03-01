load("@bazel_skylib//lib:paths.bzl", "paths")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

IntellijPluginLibraryInfo = provider(
    doc = "Flat intellij plugin library; contains the merged infomation from this library and all its dependencies.",
    fields = {
        "plugin_xmls": "Depset of files",
        "optional_plugin_xmls": "Depset of OptionalPluginXmlInfo providers",
        "java_info": "Single JavaInfo provider (depreacated, rules should get JavaInfo directly from the target)",
        "runfiles": "Runfiles for data files required by the plugin library. These are included in the plugin archive and tracked separately from library jars.",
    },
)

OptionalPluginXmlInfo = provider(
    doc = "Contains a list of structs that descibe the optional plugin xmls.",
    fields = ["optional_plugin_xmls"],
)

def _single_file(target):
    """Ensures that every target in the data/resources mapping provides exactly one file."""
    files = target[DefaultInfo].files.to_list()

    if len(files) != 1:
        fail("target %s must produce exactly one file (got %s) " % (target.label, len(files)))

    return files[0]

def _import_resources(ctx):
    """Builds a jar (aka. zip) from the resource mapping."""
    output = ctx.actions.declare_file(ctx.label.name + "_resource.jar")

    mapping = [
        "%s=%s" % (path, _single_file(target).path)
        for path, target in ctx.attr.resources.items()
    ]

    ctx.actions.run(
        inputs = [_single_file(target) for target in ctx.attr.resources.values()],
        outputs = [output],
        mnemonic = "IntellijPluginResource",
        progress_message = "Creating IntelliJ plugin resource jar for %{label}",
        executable = ctx.executable._zipper,
        arguments = ["c", output.path] + mapping,
    )

    return JavaInfo(output_jar = output, compile_jar = output)

def _import_runfiles(ctx):
    """Builds a runfile tree from the data mapping."""
    symlink_map = {
        path: _single_file(target)
        for path, target in ctx.attr.data.items()
    }

    return ctx.runfiles(symlinks = symlink_map).merge_all([
        dep[IntellijPluginLibraryInfo].runfiles
        for dep in ctx.attr.deps
        if IntellijPluginLibraryInfo in dep
    ])

def _merge_plugin_xmls(ctx):
    """Merges all dependent plugin_xmls and the current one."""
    return depset(
        direct = ctx.files.plugin_xmls,
        transitive = [
            dep[IntellijPluginLibraryInfo].plugin_xmls
            for dep in ctx.attr.deps
            if IntellijPluginLibraryInfo in dep
        ],
        order = "preorder",
    )

def _merge_optional_plugin_xmls(ctx):
    """Merges all dependent optional plugin_xmls and the current ones."""
    return depset(
        direct = [dep[OptionalPluginXmlInfo] for dep in ctx.attr.optional_plugin_xmls],
        transitive = [
            dep[IntellijPluginLibraryInfo].optional_plugin_xmls
            for dep in ctx.attr.deps
            if IntellijPluginLibraryInfo in dep
        ],
        order = "preorder",
    )

def _intellij_plugin_library_rule_impl(ctx):
    java_info = java_common.merge([dep[JavaInfo] for dep in ctx.attr.deps])

    if ctx.attr.resources:
        java_info = java_common.merge([java_info, _import_resources(ctx)])

    plugin_info = IntellijPluginLibraryInfo(
        plugin_xmls = _merge_plugin_xmls(ctx),
        optional_plugin_xmls = _merge_optional_plugin_xmls(ctx),
        java_info = java_info,
        runfiles = _import_runfiles(ctx),
    )

    default_info = DefaultInfo(runfiles = plugin_info.runfiles)

    return [plugin_info, java_info, default_info]

_intellij_plugin_library = rule(
    implementation = _intellij_plugin_library_rule_impl,
    attrs = {
        "resources": attr.string_keyed_label_dict(
            doc = "Maps a file to a specific location inside the plugin jar.",
            allow_files = True,
        ),
        "data": attr.string_keyed_label_dict(
            doc = "Maps a file to a specific location inside the plugin zip and the runfiles tree.",
            allow_files = True,
        ),
        "deps": attr.label_list(
            doc = "List of java dependencies and plugin dependencies for this library",
            providers = [[JavaInfo], [JavaInfo, IntellijPluginLibraryInfo]],
        ),
        "plugin_xmls": attr.label_list(
            doc = "List of unconditional plugin xmls.",
            allow_files = [".xml"],
        ),
        "optional_plugin_xmls": attr.label_list(
            providers = [OptionalPluginXmlInfo],
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
            cfg = "exec",
        ),
    },
)

def intellij_plugin_library(name, srcs = None, deps = None, **kwargs):
    """
    Backwards compatible implementation of the intellij_plugin_library rule.

    If sources are not None compiles them into a kotlin library. Also supports
    dependencies between plugin libraries.
    """

    deps = deps or []

    if srcs != None:
        kt_jvm_library(
            name = name + "_ktlib",
            srcs = srcs,
            deps = deps + ["//intellij_platform_sdk:plugin_api"],
            visibility = ["//visibility:private"],
        )

        deps += [name + "_ktlib"]

    _intellij_plugin_library(
        name = name,
        deps = deps,
        **kwargs
    )
