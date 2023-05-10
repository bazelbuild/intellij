"""Utility code for generating the scope for a genquery that uses the ":*" query syntax.
"""

def scopeForJavaPackage(blaze_package):
    label = Label(blaze_package)
    return [
        label,
        "//" + label.package + ":BUILD",
        "//" + label.package + ":lib" + label.name + ".jar",
        "//" + label.package + ":lib" + label.name + "-src.jar",
    ]

def scopeForAndroidPackage(blaze_package):
    label = Label(blaze_package)
    return scopeForJavaPackage(blaze_package) + [
        "//" + label.package + ":" + label.name + ".aar",
    ]
