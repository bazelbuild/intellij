#
# Description: Blaze plugin for various IntelliJ products.
#

licenses(["notice"])  # Apache 2.0

# Changelog file
filegroup(
    name = "changelog",
    srcs = ["CHANGELOG"],
    visibility = ["//:__subpackages__"],
)

# IJwB tests, run with an IntelliJ plugin SDK
test_suite(
    name = "ijwb_tests",
    tests = [
        "//base:integration_tests",
        "//base:unit_tests",
        "//dart:unit_tests",
        "//golang:unit_tests",
        "//ijwb:integration_tests",
        "//ijwb:unit_tests",
        "//java:integration_tests",
        "//java:unit_tests",
        "//kotlin:integration_tests",
        "//kotlin:unit_tests",
        "//plugin_dev:integration_tests",
        "//python:integration_tests",
        "//python:unit_tests",
        "//scala:integration_tests",
        "//scala:unit_tests",
    ],
)

# UE-specific IJwB tests
test_suite(
    name = "ijwb_ue_tests",
    tests = [
        "//golang:integration_tests",
        "//ijwb:typescript_integration_tests",
    ],
)

# ASwB tests, run with an Android Studio plugin SDK
test_suite(
    name = "aswb_tests",
    tests = [
        "//aswb:integration_tests",
        "//aswb:unit_tests",
        "//base:integration_tests",
        "//base:unit_tests",
        "//cpp:integration_tests",
        "//cpp:unit_tests",
        "//dart:unit_tests",
        "//java:integration_tests",
        "//java:unit_tests",
    ],
)

test_suite(
    name = "aswb_python_tests",
    tests = [
        "//python:integration_tests",
        "//python:unit_tests",
    ],
)

# CLwB tests, run with a CLion plugin SDK
test_suite(
    name = "clwb_tests",
    tests = [
        "//base:unit_tests",
        "//clwb:unit_tests",
        "//cpp:unit_tests",
        "//dart:unit_tests",
        "//python:unit_tests",
    ],
)
