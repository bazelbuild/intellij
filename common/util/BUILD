licenses(["notice"])

package(default_visibility = [
    # commonplugins_visibility
    "//visibility:public",
])

java_library(
    name = "binaryhelper",
    srcs = ["src/com/google/idea/common/util/HelperBinaryUtil.java"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
    ],
)

java_library(
    name = "concurrency",
    srcs = ["src/com/google/idea/common/util/ConcurrencyUtil.java"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
    ],
)

java_library(
    name = "platform",
    srcs = ["src/com/google/idea/common/util/MorePlatformUtils.java"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
    ],
)

java_library(
    name = "transactions",
    srcs = ["src/com/google/idea/common/util/Transactions.java"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
    ],
)
