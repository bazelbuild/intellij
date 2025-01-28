load("@bazel_skylib//lib:paths.bzl", "paths")
load("@rules_java//java:defs.bzl", "java_import")
load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")
load("@rules_pkg//pkg:providers.bzl", "PackageFilesInfo")

AspectFilesInfo = provider(
    fields = {
        "files": "depset of files in the library",
        "namespace": "namespace of the files",
    },
)

def _java_8_transition_impl(settings, attr):
    return {"//command_line_option:javacopt": ["-source", "8", "-target", "8"]}

_java_8_transition = transition(
    implementation = _java_8_transition_impl,
    inputs = [],
    outputs = ["//command_line_option:javacopt"],
)

def _aspect_files_impl(ctx):
    files = depset(
        transitive =
            [it[DefaultInfo].files for it in ctx.attr.files] +
            [it[DefaultInfo].files for it in ctx.attr.jars],
    )

    return [
        DefaultInfo(files = files),
        AspectFilesInfo(files = files, namespace = ctx.attr.namespace),
    ]

aspect_files = rule(
    implementation = _aspect_files_impl,
    attrs = {
        "files": attr.label_list(allow_files = True),
        "jars": attr.label_list(allow_files = True, cfg = _java_8_transition),
        "namespace": attr.string(default = "/"),
    },
)

def _aspect_pkg_impl(ctx):
    dst_to_src = {}

    for files in ctx.attr.files:
        if AspectFilesInfo in files:
            info = files[AspectFilesInfo]

            for file in info.files.to_list():
                dst = paths.join(info.namespace, file.basename)
                dst_to_src[dst] = file

        else:
            for file in files[DefaultInfo].files.to_list():
                dst_to_src[file.basename] = file

    # the manifest contains a list of all files found in the aspect library
    # every path in the manifest should start with /
    manifest = ctx.actions.declare_file("manifest_%s" % ctx.label.name)
    manifest_content = "\n".join([paths.join("/", it) for it in dst_to_src.keys()])
    ctx.actions.write(manifest, manifest_content)

    dst_to_src["manifest"] = manifest

    return [
        DefaultInfo(files = depset(dst_to_src.values())),
        PackageFilesInfo(dest_src_map = dst_to_src),
    ]

_aspect_pkg = rule(
    implementation = _aspect_pkg_impl,
    attrs = {
        "files": attr.label_list(mandatory = True, allow_files = True, providers = [[AspectFilesInfo]]),
    },
)

def aspect_library(name, namespace = "/", files = [], **kwargs):
    """
    Creates an aspect library for a set of files.

    An aspect library is a zip file imported as a java library to have more precise
    control over the file layout. Also configures all included jars to be build with
    java 8, to ensure compatability.

    Args:
        name (str): The name of the target. Also used to generate the JAR file name.
        namespace (str, optional): The parent directory inside the JAR file.
        files (list, optional): A list of files to include in the JAR.
        **kwargs: Additional arguments forwarded to the `java_import` rule.
    """

    pkg_files_name = "%s_files" % name
    pkg_zip_name = "%s_zip" % name

    _aspect_pkg(
        name = pkg_files_name,
        files = files,
    )

    pkg_zip(
        name = pkg_zip_name,
        package_file_name = "%s.jar" % name,
        package_dir = namespace,
        srcs = [pkg_files_name],
    )

    java_import(
        name = name,
        jars = [pkg_zip_name],
        **kwargs
    )
