load("@rules_android//android:rules.bzl", "android_library")

package(
    default_visibility = [
        "//aswb/testdata/projects/android1:__subpackages__",
    ],
)

android_library(
    name = "lib",
    srcs = glob(["lib/java/**/*.java"]),
    javacopts = ["-source 17 -target 17"],
    manifest = "lib/AndroidManifest.xml",
    resource_files = glob(["lib/res/**"]),
)
