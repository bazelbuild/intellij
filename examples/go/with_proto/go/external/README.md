# Packages named `external`

This directory exists to make sure that packages named `external` are imported correctly, as per https://github.com/bazelbuild/intellij/issues/6324.
Targets depending on this package (such as `//lib:lib.go`) should resolve without red squigglies.