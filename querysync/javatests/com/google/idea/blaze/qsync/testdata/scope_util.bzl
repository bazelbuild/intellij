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

def scopeForAndroidPackageWithResources(blaze_package):
    label = Label(blaze_package)
    return scopeForAndroidPackage(blaze_package) + [
        "//" + label.package + ":" + label.name + ".aar",
        "//" + label.package + ":" + label.name + ".srcjar",
        "//" + label.package + ":" + label.name + "_resources.jar",
        "//" + label.package + ":" + label.name + "_symbols/R.txt",
    ]
