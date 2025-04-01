"""Utility code for generating the scope for a genquery that uses the ":*" query syntax.

In a genquery rule, bazel requires that you specify a scope defined thus:

> The scope of the query. The query is not allowed to touch targets outside the
> transitive closure of these targets.

The macros herein generate the scope for various standard rule kinds.
"""

def scopeForJavaPackage(blaze_package):
    label = Label(blaze_package)
    return [
        label,
        "//" + label.package + ":BUILD",
        "//" + label.package + ":lib" + label.name + ".jar",
        "//" + label.package + ":lib" + label.name + "-src.jar",
    ]

def scopeForCcPackage(blaze_package):
    label = Label(blaze_package)
    return [
        label,
        "//" + label.package + ":BUILD",
    ]
