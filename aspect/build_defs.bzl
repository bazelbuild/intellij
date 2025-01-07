load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")
load("@rules_java//java:defs.bzl", "java_import")

def aspect_library(name, namespace = "/", srcs = [], **kwargs):
    """
    Creates an aspect library for a set of files.

    An aspect library is a zip file imported as a java library to have more precise
    control over the file layout.

    Args:
        name (str): The name of the target. Also used to generate the JAR file name.
        namespace (str, optional): The parent directory inside the JAR file.
        srcs (list, optional): A list of source files to include in the JAR.
        **kwargs: Additional arguments forwarded to the `java_import` rule.
    """

    pkg_name = "%s_pkg" % name

    pkg_zip(
        name = pkg_name,
        package_file_name = "%s.jar" % name,
        package_dir = namespace,
        srcs = srcs,
    )

    java_import(
        name = name,
        jars = [pkg_name],
        **kwargs,
    )
