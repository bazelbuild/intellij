load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")
load("@rules_java//java:defs.bzl", "java_import")
load("@bazel_skylib//lib:paths.bzl", "paths")

def _java_8_transition_impl(settings, attr):
    return {"//command_line_option:javacopt": ["-source", "8", "-target", "8"]}

_java_8_transition = transition(
    implementation = _java_8_transition_impl,
    inputs = [],
    outputs = ["//command_line_option:javacopt"],
)

def _java_8_cfg_impl(ctx):
    files = []

    for jar in ctx.attr.jars:
        files.extend(jar[DefaultInfo].files.to_list())

    return [DefaultInfo(files = depset(files))]

_java_8_cfg = rule(
    implementation = _java_8_cfg_impl,
    attrs = {
        "jars": attr.label_list(mandatory = True, allow_files = True, cfg = _java_8_transition),
    },
)

def aspect_library(name, namespace = "/", files = [], jars = [], **kwargs):
    """
    Creates an aspect library for a set of files.

    An aspect library is a zip file imported as a java library to have more precise
    control over the file layout. Also configures all included jars to be build with
    java 8, to ensure compatability.

    Args:
        name (str): The name of the target. Also used to generate the JAR file name.
        namespace (str, optional): The parent directory inside the JAR file.
        files (list, optional): A list of files to include in the JAR.
        jars (list, optional): A list of jars to include in the JAR (configured for java 8).
        **kwargs: Additional arguments forwarded to the `java_import` rule.
    """

    cfg_java_name = "%s_java" % name
    _java_8_cfg(
        name = cfg_java_name,
        jars = jars,
    )

    pkg_zip_name = "%s_zip" % name
    pkg_zip(
        name = pkg_zip_name,
        package_file_name = "%s.jar" % name,
        package_dir = namespace,
        srcs = files + [cfg_java_name],
    )

    java_import(
        name = name,
        jars = [pkg_zip_name],
        **kwargs
    )
