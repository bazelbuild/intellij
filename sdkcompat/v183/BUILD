# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

java_library(
    name = "v183",
    srcs = glob([
        "com/google/idea/sdkcompat/openapi/**",
        "com/google/idea/sdkcompat/run/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cidr/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/javascript/**",
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/javascript/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:junit",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/javascript",
        "//third_party/python",
    ],
)
