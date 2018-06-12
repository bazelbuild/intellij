"""Implementation of IntelliJ-specific information collecting aspect."""

load(
    ":artifacts.bzl",
    "artifact_location",
    "artifacts_from_target_list_attr",
    "is_external_artifact",
    "sources_from_target",
    "struct_omit_none",
    "to_artifact_location",
)

# Compile-time dependency attributes, grouped by type.
DEPS = [
    "_cc_toolchain",  # From cc rules
    "_stl",  # From cc rules
    "malloc",  # From cc_binary rules
    "_java_toolchain",  # From java rules
    "deps",
    "exports",
    "java_lib",  # From old proto_library rules
    "_android_sdk",  # from android rules
    "aidl_lib",  # from android_sdk
    "_scala_toolchain",  # From scala rules
    "test_app",  # android_instrumentation_test
    "instruments",  # android_instrumentation_test
]

# Run-time dependency attributes, grouped by type.
RUNTIME_DEPS = [
    "runtime_deps",
]

PREREQUISITE_DEPS = []

# Dependency type enum
COMPILE_TIME = 0
RUNTIME = 1

##### Helpers

def source_directory_tuple(resource_file):
    """Creates a tuple of (exec_path, root_exec_path_fragment, is_source, is_external)."""
    relative_path = str(android_common.resource_source_directory(resource_file))
    root_exec_path_fragment = resource_file.root.path if not resource_file.is_source else None
    return (
        relative_path if resource_file.is_source else root_exec_path_fragment + "/" + relative_path,
        root_exec_path_fragment,
        resource_file.is_source,
        is_external_artifact(resource_file.owner),
    )

def all_unique_source_directories(resources):
    """Builds a list of unique ArtifactLocation protos."""

    # Sets can contain tuples, but cannot contain structs.
    # Use set of tuples to unquify source directories.
    source_directory_tuples = depset([source_directory_tuple(f) for f in resources])
    return [
        to_artifact_location(
            exec_path,
            root_path_fragment,
            is_source,
            is_external,
        )
        for (exec_path, root_path_fragment, is_source, is_external) in source_directory_tuples
    ]

def build_file_artifact_location(ctx):
    """Creates an ArtifactLocation proto representing a location of a given BUILD file."""
    return to_artifact_location(
        ctx.build_file_path,
        ctx.build_file_path,
        True,
        is_external_artifact(ctx.label),
    )

def get_source_jars(output):
    if hasattr(output, "source_jars"):
        return output.source_jars
    if hasattr(output, "source_jar"):
        return [output.source_jar]
    return []

def library_artifact(java_output):
    """Creates a LibraryArtifact representing a given java_output."""
    if java_output == None or java_output.class_jar == None:
        return None
    src_jars = get_source_jars(java_output)
    return struct_omit_none(
        jar = artifact_location(java_output.class_jar),
        interface_jar = artifact_location(java_output.ijar),
        source_jar = artifact_location(src_jars[0]) if src_jars else None,
        source_jars = [artifact_location(f) for f in src_jars],
    )

def annotation_processing_jars(annotation_processing):
    """Creates a LibraryArtifact representing Java annotation processing jars."""
    src_jar = annotation_processing.source_jar
    return struct_omit_none(
        jar = artifact_location(annotation_processing.class_jar),
        source_jar = artifact_location(src_jar),
        source_jars = [artifact_location(src_jar)] if src_jar else None,
    )

def jars_from_output(output):
    """Collect jars for intellij-resolve-files from Java output."""
    if output == None:
        return []
    return [
        jar
        for jar in ([output.class_jar, output.ijar] + get_source_jars(output))
        if jar != None and not jar.is_source
    ]

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

def targets_to_labels(targets):
    """Returns a set of label strings for the given targets."""
    return depset([str(target.label) for target in targets])

def list_omit_none(value):
    """Returns a list of the value, or the empty list if None."""
    return [value] if value else []

def is_valid_aspect_target(target):
    """Returns whether the target has had the aspect run on it."""
    return hasattr(target, "intellij_info")

def get_aspect_ids(ctx, target):
    """Returns the all aspect ids, filtering out self."""
    aspect_ids = None
    if hasattr(ctx, "aspect_ids"):
        aspect_ids = ctx.aspect_ids
    elif hasattr(target, "aspect_ids"):
        aspect_ids = target.aspect_ids
    else:
        return None
    return [aspect_id for aspect_id in aspect_ids if "intellij_info_aspect" not in aspect_id]

def make_target_key(label, aspect_ids):
    """Returns a TargetKey proto struct from a target."""
    return struct_omit_none(
        label = str(label),
        aspect_ids = tuple(aspect_ids) if aspect_ids else None,
    )

def make_dep(dep, dependency_type):
    """Returns a Dependency proto struct."""
    return struct(
        target = dep.intellij_info.target_key,
        dependency_type = dependency_type,
    )

def make_deps(deps, dependency_type):
    """Returns a list of Dependency proto structs."""
    return [make_dep(dep, dependency_type) for dep in deps]

def make_dep_from_label(label, dependency_type):
    """Returns a Dependency proto struct from a label."""
    return struct(
        target = struct(label = str(label)),
        dependency_type = dependency_type,
    )

def update_set_in_dict(input_dict, key, other_set):
    """Updates depset in dict, merging it with another depset."""
    input_dict[key] = input_dict.get(key, depset()) | other_set

##### Builders for individual parts of the aspect output

def collect_py_info(target, ctx, ide_info, ide_info_file, output_groups):
    """Updates Python-specific output groups, returns false if not a Python target."""
    if not hasattr(target, "py"):
        return False

    ide_info["py_ide_info"] = struct_omit_none(
        sources = sources_from_target(ctx),
    )
    transitive_sources = target.py.transitive_sources

    update_set_in_dict(output_groups, "intellij-info-py", depset([ide_info_file]))
    update_set_in_dict(output_groups, "intellij-compile-py", transitive_sources)
    update_set_in_dict(output_groups, "intellij-resolve-py", transitive_sources)
    return True

def _get_go_proto_info(target):
    if hasattr(target, "aspect_proto_go_api_info"):
        return target.aspect_proto_go_api_info
    if hasattr(target, "proto_go_api_info"):
        return target.proto_go_api_info
    return None

def _collect_generated_proto_go_sources(target):
    """Returns a depset of proto go source files generated by this target."""
    go_proto_info = _get_go_proto_info(target)
    if not go_proto_info:
        return None
    files = getattr(go_proto_info, "files_to_build", [])
    return [f for f in files if f.basename.endswith(".pb.go")]

def collect_go_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates Go-specific output groups, returns false if not a recognized Go target."""
    sources = []
    generated = []

    # currently there's no Go Skylark API, with the only exception being proto_library targets
    if ctx.rule.kind in [
        "go_binary",
        "go_library",
        "go_test",
        "go_appengine_binary",
        "go_appengine_library",
        "go_appengine_test",
    ]:
        sources += [f for src in getattr(ctx.rule.attr, "srcs", []) for f in src.files]
        generated += [f for f in sources if not f.is_source]
    elif ctx.rule.kind == "go_wrap_cc":
        # we want the .go file, but the rule only provides .a and .x files
        # add those to the output group to make sure the .go file gets built,
        # then manually construct the .go file in the sync plugin
        # TODO(chaorenl): change this if we ever get the .go file from a provider
        generated += target.files.to_list()
    else:
        proto_sources = _collect_generated_proto_go_sources(target)
        if not proto_sources:
            return False
        sources += proto_sources
        generated += proto_sources

    import_path = None
    go_semantics = getattr(semantics, "go", None)
    if go_semantics:
        import_path = go_semantics.get_import_path(ctx)

    ide_info["go_ide_info"] = struct_omit_none(
        sources = [artifact_location(f) for f in sources],
        import_path = import_path,
    )

    # TODO(brendandouglas): remove once enough Bazel users are on a version with the changed name
    old_compile_files = target.output_group("files_to_compile_INTERNAL_")
    compile_files = target.output_group("compilation_outputs")
    compile_files = depset(generated, transitive = [compile_files, old_compile_files])

    update_set_in_dict(output_groups, "intellij-info-go", depset([ide_info_file]))
    update_set_in_dict(output_groups, "intellij-compile-go", compile_files)
    update_set_in_dict(output_groups, "intellij-resolve-go", depset(generated))
    return True

def collect_cpp_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates C++-specific output groups, returns false if not a C++ target."""
    if not hasattr(target, "cc"):
        return False

    sources = artifacts_from_target_list_attr(ctx, "srcs")
    headers = artifacts_from_target_list_attr(ctx, "hdrs")
    textual_headers = artifacts_from_target_list_attr(ctx, "textual_hdrs")

    target_includes = []
    if hasattr(ctx.rule.attr, "includes"):
        target_includes = ctx.rule.attr.includes
    target_defines = []
    if hasattr(ctx.rule.attr, "defines"):
        target_defines = ctx.rule.attr.defines
    target_copts = []
    if hasattr(ctx.rule.attr, "copts"):
        target_copts += ctx.rule.attr.copts
    if hasattr(semantics, "cc") and hasattr(semantics.cc, "get_default_copts"):
        target_copts += semantics.cc.get_default_copts(ctx)

    cc_provider = target.cc

    c_info = struct_omit_none(
        source = sources,
        header = headers,
        textual_header = textual_headers,
        target_include = target_includes,
        target_define = target_defines,
        target_copt = target_copts,
        transitive_include_directory = cc_provider.include_directories,
        transitive_quote_include_directory = cc_provider.quote_include_directories,
        transitive_define = cc_provider.defines,
        transitive_system_include_directory = cc_provider.system_include_directories,
    )
    ide_info["c_ide_info"] = c_info
    resolve_files = cc_provider.transitive_headers

    # TODO(brendandouglas): target to cpp files only
    # TODO(brendandouglas): remove once enough Bazel users are on a version with the changed name
    old_compile_files = target.output_group("files_to_compile_INTERNAL_")
    compile_files = target.output_group("compilation_outputs")
    compile_files = depset(transitive = [compile_files, old_compile_files])

    update_set_in_dict(output_groups, "intellij-info-cpp", depset([ide_info_file]))
    update_set_in_dict(output_groups, "intellij-compile-cpp", compile_files)
    update_set_in_dict(output_groups, "intellij-resolve-cpp", resolve_files)
    return True

def collect_c_toolchain_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates cc_toolchain-relevant output groups, returns false if not a cc_toolchain target."""
    if ctx.rule.kind != "cc_toolchain" or cc_common.CcToolchainInfo not in target:
        return False

    # TODO(brendandouglas): remove cc_fragment dependency once we no longer have to support bazel
    # versions prior to 0.13
    # This should exist because we requested it in our aspect definition.
    cc_fragment = ctx.fragments.cpp
    cpp_options = cc_fragment.cxx_options(ctx.features)
    c_options = cc_fragment.c_options
    compiler_options = cc_fragment.compiler_options(ctx.features)
    unfiltered_compiler_options = cc_fragment.unfiltered_compiler_options(ctx.features)

    cpp_toolchain = target[cc_common.CcToolchainInfo]

    #  cpp_options = cpp_toolchain.cxx_options()
    #  compiler_options = cpp_toolchain.compiler_options()
    #  c_options = cpp_toolchain.c_options()
    #  unfiltered_compiler_options = cpp_toolchain.unfiltered_compiler_options([])
    if hasattr(semantics, "cc"):
        cpp_options = semantics.cc.augment_toolchain_cxx_options(cpp_options)

    c_toolchain_info = struct_omit_none(
        target_name = cpp_toolchain.target_gnu_system_name,
        base_compiler_option = compiler_options,
        c_option = c_options,
        cpp_option = cpp_options,
        unfiltered_compiler_option = unfiltered_compiler_options,
        cpp_executable = str(cpp_toolchain.compiler_executable),
        built_in_include_directory = [str(d) for d in cpp_toolchain.built_in_include_directories],
    )
    ide_info["c_toolchain_ide_info"] = c_toolchain_info
    update_set_in_dict(output_groups, "intellij-info-cpp", depset([ide_info_file]))
    return True

def get_java_provider(target):
    """Find a provider exposing java compilation/outputs data."""
    if hasattr(target, "proto_java"):
        return target.proto_java
    if hasattr(target, "java"):
        return target.java
    if hasattr(target, "scala"):
        return target.scala
    if hasattr(target, "kt") and hasattr(target.kt, "outputs"):
        return target.kt

    # TODO(brendandouglas): use java_common.provider preferentially
    if java_common.provider in target:
        return target[java_common.provider]
    return None

def collect_java_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates Java-specific output groups, returns false if not a Java target."""
    java = get_java_provider(target)
    if not java or not hasattr(java, "outputs") or not java.outputs:
        return False

    java_semantics = semantics.java if hasattr(semantics, "java") else None
    if java_semantics and java_semantics.skip_target(target, ctx):
        return False

    ide_info_files = depset()
    sources = sources_from_target(ctx)
    jars = [library_artifact(output) for output in java.outputs.jars]
    class_jars = [output.class_jar for output in java.outputs.jars if output and output.class_jar]
    output_jars = [jar for output in java.outputs.jars for jar in jars_from_output(output)]
    resolve_files = depset(output_jars)
    compile_files = depset(class_jars)

    gen_jars = []
    if (hasattr(java, "annotation_processing") and
        java.annotation_processing and
        java.annotation_processing.enabled):
        gen_jars = [annotation_processing_jars(java.annotation_processing)]
        resolve_files = resolve_files | depset([
            jar
            for jar in [
                java.annotation_processing.class_jar,
                java.annotation_processing.source_jar,
            ]
            if jar != None and not jar.is_source
        ])
        compile_files = compile_files | depset([
            jar
            for jar in [java.annotation_processing.class_jar]
            if jar != None and not jar.is_source
        ])

    jdeps = None
    if hasattr(java.outputs, "jdeps"):
        jdeps = artifact_location(java.outputs.jdeps)

    java_sources, gen_java_sources, srcjars = divide_java_sources(ctx)

    if java_semantics:
        srcjars = java_semantics.filter_source_jars(target, ctx, srcjars)

    package_manifest = None
    if java_sources:
        package_manifest = build_java_package_manifest(ctx, target, java_sources, ".java-manifest")
        ide_info_files = ide_info_files | depset([package_manifest])

    filtered_gen_jar = None
    if java_sources and (gen_java_sources or srcjars):
        filtered_gen_jar, filtered_gen_resolve_files = build_filtered_gen_jar(
            ctx,
            target,
            java,
            gen_java_sources,
            srcjars,
        )
        resolve_files = resolve_files | filtered_gen_resolve_files

    java_info = struct_omit_none(
        sources = sources,
        jars = jars,
        jdeps = jdeps,
        generated_jars = gen_jars,
        package_manifest = artifact_location(package_manifest),
        filtered_gen_jar = filtered_gen_jar,
        main_class = getattr(ctx.rule.attr, "main_class", None),
        test_class = getattr(ctx.rule.attr, "test_class", None),
    )

    ide_info["java_ide_info"] = java_info
    ide_info_files += depset([ide_info_file])
    update_set_in_dict(output_groups, "intellij-info-java", ide_info_files)
    update_set_in_dict(output_groups, "intellij-compile-java", compile_files)
    update_set_in_dict(output_groups, "intellij-resolve-java", resolve_files)
    return True

def _package_manifest_file_argument(f):
    artifact = artifact_location(f)
    is_external = "1" if is_external_artifact(f.owner) else "0"
    return artifact.root_execution_path_fragment + "," + artifact.relative_path + "," + is_external

def build_java_package_manifest(ctx, target, source_files, suffix):
    """Builds the java package manifest for the given source files."""
    output = ctx.new_file(target.label.name + suffix)

    args = []
    args += ["--output_manifest", output.path]
    args += ["--sources"]
    args += [":".join([_package_manifest_file_argument(f) for f in source_files])]
    argfile = ctx.new_file(
        ctx.configuration.bin_dir,
        target.label.name + suffix + ".params",
    )
    ctx.file_action(output = argfile, content = "\n".join(args))

    ctx.action(
        inputs = source_files + [argfile],
        outputs = [output],
        executable = ctx.executable._package_parser,
        arguments = ["@" + argfile.path],
        mnemonic = "JavaPackageManifest",
        progress_message = "Parsing java package strings for " + str(target.label),
    )
    return output

def build_filtered_gen_jar(ctx, target, java, gen_java_sources, srcjars):
    """Filters the passed jar to contain only classes from the given manifest."""
    jar_artifacts = []
    source_jar_artifacts = []
    for jar in java.outputs.jars:
        if jar.ijar:
            jar_artifacts.append(jar.ijar)
        elif jar.class_jar:
            jar_artifacts.append(jar.class_jar)
        if hasattr(jar, "source_jars") and jar.source_jars:
            source_jar_artifacts.extend(jar.source_jars)
        elif hasattr(jar, "source_jar") and jar.source_jar:
            source_jar_artifacts.append(jar.source_jar)

    filtered_jar = ctx.new_file(target.label.name + "-filtered-gen.jar")
    filtered_source_jar = ctx.new_file(target.label.name + "-filtered-gen-src.jar")
    args = []
    for jar in jar_artifacts:
        args += ["--filter_jar", jar.path]
    for jar in source_jar_artifacts:
        args += ["--filter_source_jar", jar.path]
    args += ["--filtered_jar", filtered_jar.path]
    args += ["--filtered_source_jar", filtered_source_jar.path]
    if gen_java_sources:
        for java_file in gen_java_sources:
            args += ["--keep_java_file", java_file.path]
    if srcjars:
        for source_jar in srcjars:
            args += ["--keep_source_jar", source_jar.path]
    ctx.action(
        inputs = jar_artifacts + source_jar_artifacts + gen_java_sources + srcjars,
        outputs = [filtered_jar, filtered_source_jar],
        executable = ctx.executable._jar_filter,
        arguments = args,
        mnemonic = "JarFilter",
        progress_message = "Filtering generated code for " + str(target.label),
    )
    output_jar = struct(
        jar = artifact_location(filtered_jar),
        source_jar = artifact_location(filtered_source_jar),
    )
    intellij_resolve_files = depset([filtered_jar, filtered_source_jar])
    return output_jar, intellij_resolve_files

def divide_java_sources(ctx):
    """Divide sources into plain java, generated java, and srcjars."""

    java_sources = []
    gen_java_sources = []
    srcjars = []
    if hasattr(ctx.rule.attr, "srcs"):
        srcs = ctx.rule.attr.srcs
        for src in srcs:
            for f in src.files:
                if f.basename.endswith(".java"):
                    if f.is_source:
                        java_sources.append(f)
                    else:
                        gen_java_sources.append(f)
                elif f.basename.endswith(".srcjar"):
                    srcjars.append(f)

    return java_sources, gen_java_sources, srcjars

def collect_android_info(target, ctx, semantics, ide_info, ide_info_file, output_groups):
    """Updates Android-specific output groups, returns false if not a Android target."""
    if not hasattr(target, "android"):
        return False

    android_semantics = semantics.android if hasattr(semantics, "android") else None
    extra_ide_info = android_semantics.extra_ide_info(target, ctx) if android_semantics else {}

    android = target.android
    android_info = struct_omit_none(
        java_package = android.java_package,
        idl_import_root = android.idl.import_root if hasattr(android.idl, "import_root") else None,
        manifest = artifact_location(android.manifest),
        apk = artifact_location(android.apk),
        dependency_apk = [artifact_location(apk) for apk in android.apks_under_test],
        has_idl_sources = android.idl.output != None,
        idl_jar = library_artifact(android.idl.output),
        generate_resource_class = android.defines_resources,
        resources = all_unique_source_directories(android.resources),
        resource_jar = library_artifact(android.resource_jar),
        **extra_ide_info
    )
    resolve_files = depset(jars_from_output(android.idl.output))

    if android.manifest and not android.manifest.is_source:
        resolve_files = resolve_files | depset([android.manifest])

    ide_info["android_ide_info"] = android_info
    update_set_in_dict(output_groups, "intellij-info-android", depset([ide_info_file]))
    update_set_in_dict(output_groups, "intellij-resolve-android", resolve_files)
    return True

def collect_android_sdk_info(ctx, ide_info, ide_info_file, output_groups):
    """Updates android_sdk-relevant groups, returns false if not an android_sdk target."""
    if ctx.rule.kind != "android_sdk":
        return False
    android_jar_file = list(ctx.rule.attr.android_jar.files)[0]
    ide_info["android_sdk_ide_info"] = struct(
        android_jar = artifact_location(android_jar_file),
    )
    update_set_in_dict(output_groups, "intellij-info-android", depset([ide_info_file]))
    return True

def collect_aar_import_info(ctx, ide_info, ide_info_file, output_groups):
    """Updates android aar_import-relevant groups, returns false if not an aar_import target."""
    if ctx.rule.kind != "aar_import":
        return False
    if not hasattr(ctx.rule.attr, "aar"):
        return False
    aar_file = list(ctx.rule.attr.aar.files)[0]
    ide_info["android_aar_ide_info"] = struct(
        aar = artifact_location(aar_file),
    )
    update_set_in_dict(output_groups, "intellij-info-android", depset([ide_info_file]))
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

def collect_java_toolchain_info(target, ide_info, ide_info_file, output_groups):
    """Updates java_toolchain-relevant output groups, returns false if not a java_toolchain target."""
    if not hasattr(target, "java_toolchain"):
        return False
    toolchain_info = target.java_toolchain
    javac_jar_file = toolchain_info.javac_jar if hasattr(toolchain_info, "javac_jar") else None
    ide_info["java_toolchain_ide_info"] = struct_omit_none(
        source_version = toolchain_info.source_version,
        target_version = toolchain_info.target_version,
        javac_jar = artifact_location(javac_jar_file),
    )
    update_set_in_dict(output_groups, "intellij-info-java", depset([ide_info_file]))
    return True

##### Main aspect function

def intellij_info_aspect_impl(target, ctx, semantics):
    """Aspect implementation function."""
    tags = ctx.rule.attr.tags
    if "no-ide" in tags:
        return struct()

    rule_attrs = ctx.rule.attr

    # Collect direct dependencies
    direct_dep_targets = collect_targets_from_attrs(
        rule_attrs,
        semantics_extra_deps(DEPS, semantics, "extra_deps"),
    )
    direct_deps = make_deps(direct_dep_targets, COMPILE_TIME)

    # Add exports from direct dependencies
    exported_deps_from_deps = []
    for dep in direct_dep_targets:
        exported_deps_from_deps = exported_deps_from_deps + dep.intellij_info.export_deps

    # Combine into all compile time deps
    compiletime_deps = direct_deps + exported_deps_from_deps

    # Propagate my own exports
    export_deps = []
    if hasattr(target, "java"):
        transitive_exports = target.java.transitive_exports
        export_deps = [make_dep_from_label(label, COMPILE_TIME) for label in transitive_exports]

        # Empty android libraries export all their dependencies.
        if ctx.rule.kind == "android_library":
            if not hasattr(rule_attrs, "srcs") or not ctx.rule.attr.srcs:
                export_deps = export_deps + compiletime_deps
    export_deps = list(depset(export_deps))

    # runtime_deps
    runtime_dep_targets = collect_targets_from_attrs(
        rule_attrs,
        semantics_extra_deps(RUNTIME_DEPS, semantics, "extra_runtime_deps"),
    )
    runtime_deps = make_deps(runtime_dep_targets, RUNTIME)
    all_deps = list(depset(compiletime_deps + runtime_deps))

    # extra prerequisites
    extra_prerequisite_targets = collect_targets_from_attrs(
        rule_attrs,
        semantics_extra_deps(PREREQUISITE_DEPS, semantics, "extra_prerequisites"),
    )

    # Roll up output files from my prerequisites
    prerequisites = direct_dep_targets + runtime_dep_targets + extra_prerequisite_targets
    output_groups = dict()
    for dep in prerequisites:
        for k, v in dep.intellij_info.output_groups.items():
            update_set_in_dict(output_groups, k, v)

    # Initialize the ide info dict, and corresponding output file
    # This will be passed to each language-specific handler to fill in as required
    file_name = target.label.name
    aspect_ids = get_aspect_ids(ctx, target)
    if aspect_ids:
        aspect_hash = hash(".".join(aspect_ids))
        file_name = file_name + "-" + str(aspect_hash)
    file_name = file_name + ".intellij-info.txt"
    ide_info_file = ctx.new_file(file_name)

    target_key = make_target_key(target.label, aspect_ids)
    ide_info = dict(
        key = target_key,
        kind_string = ctx.rule.kind,
        deps = list(all_deps),
        build_file_artifact_location = build_file_artifact_location(ctx),
        tags = tags,
        features = ctx.features,
    )

    # Collect test info
    ide_info["test_info"] = build_test_info(ctx)

    handled = False
    handled = collect_py_info(target, ctx, ide_info, ide_info_file, output_groups) or handled
    handled = collect_cpp_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled
    handled = collect_c_toolchain_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled
    handled = collect_go_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled
    handled = collect_java_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled
    handled = collect_java_toolchain_info(target, ide_info, ide_info_file, output_groups) or handled
    handled = collect_android_info(target, ctx, semantics, ide_info, ide_info_file, output_groups) or handled
    handled = collect_android_sdk_info(ctx, ide_info, ide_info_file, output_groups) or handled
    handled = collect_aar_import_info(ctx, ide_info, ide_info_file, output_groups) or handled

    # Any extra ide info
    if hasattr(semantics, "extra_ide_info"):
        handled = semantics.extra_ide_info(target, ctx, ide_info, ide_info_file, output_groups) or handled

    # Add to generic output group if it's not handled by a language-specific handler
    if not handled:
        update_set_in_dict(output_groups, "intellij-info-generic", depset([ide_info_file]))

    # Output the ide information file.
    info = struct_omit_none(**ide_info)
    ctx.file_action(ide_info_file, info.to_proto())

    # Return providers.
    return struct_omit_none(
        output_groups = output_groups,
        intellij_info = struct(
            target_key = target_key,
            output_groups = output_groups,
            export_deps = export_deps,
        ),
    )

def semantics_extra_deps(base, semantics, name):
    if not hasattr(semantics, name):
        return base
    extra_deps = getattr(semantics, name)
    return base + extra_deps

def make_intellij_info_aspect(aspect_impl, semantics):
    """Creates the aspect given the semantics."""
    tool_label = semantics.tool_label
    deps = semantics_extra_deps(DEPS, semantics, "extra_deps")
    runtime_deps = semantics_extra_deps(RUNTIME_DEPS, semantics, "extra_runtime_deps")
    prerequisite_deps = semantics_extra_deps(PREREQUISITE_DEPS, semantics, "extra_prerequisites")

    attr_aspects = deps + runtime_deps + prerequisite_deps

    return aspect(
        attrs = {
            "_package_parser": attr.label(
                default = tool_label("PackageParser"),
                cfg = "host",
                executable = True,
                allow_files = True,
            ),
            "_jar_filter": attr.label(
                default = tool_label("JarFilter"),
                cfg = "host",
                executable = True,
                allow_files = True,
            ),
        },
        attr_aspects = attr_aspects,
        fragments = ["cpp"],
        implementation = aspect_impl,
        required_aspect_providers = [["proto_java"], ["aspect_proto_go_api_info"], ["dart"]],
    )
