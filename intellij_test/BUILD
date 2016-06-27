# Description:
#
# Common test utilities for IntelliJ plugins.

package(default_visibility = ["//visibility:public"])

java_library(
    name = "lib",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//intellij-platform-sdk:plugin_api_for_tests",
        "//third_party:jsr305",
        "//third_party:test_lib",
    ],
)
