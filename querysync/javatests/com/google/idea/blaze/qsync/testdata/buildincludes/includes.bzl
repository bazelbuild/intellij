"""Test module for the purpose of including something in a BUILD file."""

load("@rules_java//java:defs.bzl", "java_library")

def my_java_library(name):
    java_library(
        name = name,
        srcs = native.glob(["*.java"]),
        deps = [],
    )
