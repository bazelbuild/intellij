load(":scope_util.bzl", "scopeForAndroidPackage", "scopeForJavaPackage")

genquery(
    name = "JavaLibraryNoDepsQuery",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"),
)

genquery(
    name = "JavaLibraryExternalDepQuery",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep"),
)

genquery(
    name = "JavaLibraryInternalDepQuery",
    expression = " + ".join([
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep:*",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep") +
            scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"),
)

genquery(
    name = "JavaLibraryTransitiveDepQuery",
    expression = " + ".join([
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/transitivedep:*",
        "//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/transitivedep") +
            scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep"),
)

genquery(
    name = "JavaLibraryProtoDepQuery",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/protodep:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/protodep"),
)

genquery(
    name = "JavaLibraryMultiTargets",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:nodeps") +
            scopeForJavaPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:externaldep"),
)

genquery(
    name = "AndroidLibQuery",
    expression = "//querysync/javatests/com/google/idea/blaze/qsync/testdata/android:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForAndroidPackage("//querysync/javatests/com/google/idea/blaze/qsync/testdata/android"),
)

java_test(
    name = "BuildGraphTest",
    srcs = ["BuildGraphTest.java"],
    data = [
        ":AndroidLibQuery",
        ":JavaLibraryExternalDepQuery",
        ":JavaLibraryInternalDepQuery",
        ":JavaLibraryMultiTargets",
        ":JavaLibraryNoDepsQuery",
        ":JavaLibraryProtoDepQuery",
        ":JavaLibraryTransitiveDepQuery",
    ],
    deps = [
        "//java/com/google/devtools/build/runtime:runfiles",
        "//querysync/java/com/google/idea/blaze/qsync",
        "//shared",
        "@junit//jar",
        "@truth//jar",
    ],
)
