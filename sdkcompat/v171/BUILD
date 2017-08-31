# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

java_library(
    name = "v171",
    srcs = glob([
        "com/google/idea/sdkcompat/codestyle/**",
        "com/google/idea/sdkcompat/profile/**",
        "com/google/idea/sdkcompat/run/**",
        "com/google/idea/sdkcompat/smrunner/**",
        "com/google/idea/sdkcompat/transactions/**",
        "com/google/idea/sdkcompat/ui/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/java/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/clion/**",
            "com/google/idea/sdkcompat/python/**",
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/dart/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/python/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:junit",
        "@jsr305_annotations//jar",
    ] + select_for_ide(
        clion = ["//third_party/python"],
        intellij = ["//third_party/python"],
    ),
)
