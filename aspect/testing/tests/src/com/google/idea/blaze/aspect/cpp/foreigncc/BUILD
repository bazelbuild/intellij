load("@rules_java//java:defs.bzl", "java_test")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("//aspect/testing/rules:intellij_aspect_test_fixture.bzl", "intellij_aspect_test")

intellij_aspect_test(
    name = "ForeignCcTest",
    srcs = ["ForeignCcTest.kt"],
    aspect_deps = ["@clwb_virtual_includes_project//main"],
)
