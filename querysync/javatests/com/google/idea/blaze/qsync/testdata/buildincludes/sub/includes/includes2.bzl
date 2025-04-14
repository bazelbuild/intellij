"""Test module for the purpose of including something in a BUILD file."""

load("@rules_java//java:java_library.bzl", "java_library")

def my_java_library2(name):
    java_library(
        name = name,
        srcs = native.glob(["*.java"]),
        deps = [],
    )
