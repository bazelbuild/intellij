load(
    ":scope_util.bzl",
    "scopeForAndroidPackage",
    "scopeForAndroidPackageWithResources",
    "scopeForJavaPackage",
)

package(default_visibility = [
    "//base:__subpackages__",
    "//querysync/javatests/com/google/idea/blaze/qsync:__subpackages__",
])

genquery(
    name = "java_library_no_deps_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"),
)

genquery(
    name = "java_library_external_dep_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep"),
)

genquery(
    name = "java_library_internal_dep_query",
    expression = " + ".join([
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep:*",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep") +
            scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"),
)

genquery(
    name = "java_library_transitive_dep_query",
    expression = " + ".join([
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/transitivedep:*",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/transitivedep") +
            scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep"),
)

genquery(
    name = "java_library_proto_dep_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/protodep:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/protodep"),
)

genquery(
    name = "java_library_multi_targets",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:nodeps") +
            scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:externaldep"),
)

genquery(
    name = "java_library_nested_package",
    expression = " + ".join([
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/nested:*",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/nested/inner:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/nested") +
            scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/nested/inner"),
)

genquery(
    name = "android_lib_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/android:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForAndroidPackageWithResources("//querysync/javatests/com/google/idea/blaze/qsync/testdata/android"),
)

genquery(
    name = "android_aidl_source_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/aidl:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForAndroidPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/aidl"),
)

genquery(
    name = "java_exported_dep_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/exports:*",
    opts = ["--output=streamed_proto"],
    scope =
        scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/exports:exports") +
        scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/exports:exported-collect"),
)

genquery(
    name = "buildincludes_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes:*",
    opts = ["--output=streamed_proto"],
    scope =
        scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes:mylib"),
)

genquery(
    name = "filegroup_query",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/filegroup:*",
    opts = ["--output=streamed_proto"],
    scope =
        ["//querysync/javatests/com/google/idea/blaze/qsync/testdata/filegroup:sources"] +
        scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/filegroup:filegroup"),
)

java_library(
    name = "testdata",
    srcs = ["TestData.java"],
    data = [
        ":android_aidl_source_query",
        ":android_lib_query",
        ":buildincludes_query",
        ":filegroup_query",
        ":java_exported_dep_query",
        ":java_library_external_dep_query",
        ":java_library_internal_dep_query",
        ":java_library_multi_targets",
        ":java_library_nested_package",
        ":java_library_no_deps_query",
        ":java_library_proto_dep_query",
        ":java_library_transitive_dep_query",
    ],
    deps = [
        "@bazel_tools//tools/java/runfiles",
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "project_protos",
    testonly = 1,
    srcs = ["ProjectProtos.java"],
    deps = [
        ":testdata",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//shared:exception",
        "@com_google_guava_guava//jar",
    ],
)
