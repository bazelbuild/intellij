#
# Description: Blaze plugin for various IntelliJ products.
#

# IJwB tests, run with an IntelliJ plugin SDK
test_suite(
    name = "ijwb_tests",
    tests = [
        "//blaze-base:integration_tests",
        "//blaze-base:unit_tests",
        "//blaze-java:integration_tests",
        "//blaze-java:unit_tests",
        "//blaze-plugin-dev:integration_tests",
    ],
)

# ASwB tests, run with an Android Studio plugin SDK
test_suite(
    name = "aswb_tests",
    tests = [
        "//aswb:unit_tests",
        "//blaze-base:unit_tests",
        "//blaze-java:unit_tests",
    ],
)

# Version file
filegroup(
    name = "version",
    srcs = ["VERSION"],
    visibility = ["//visibility:public"],
)

