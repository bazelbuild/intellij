load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")
load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)

licenses(["notice"])

java_library(
    name = "foolib",
    srcs = ["Foo.java"],
)

java_binary(
    name = "foo",
    srcs = ["FooMain.java"],
    main_class = "com.google.MyMainClass",
    deps = [":foolib"],
)

intellij_aspect_test_fixture(
    name = "foo_fixture",
    deps = [":foo"],
)

java_test(
    name = "JavaBinaryTest",
    srcs = ["JavaBinaryTest.java"],
    data = [":foo_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
