# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])  # Apache 2.0

java_library(
    name = "v193",
    srcs = glob([
        "com/google/idea/sdkcompat/configuration/**",
        "com/google/idea/sdkcompat/openapi/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/python/*.java",
        "com/google/idea/sdkcompat/util/**",
        "com/google/idea/sdkcompat/run/**",
        "com/google/idea/sdkcompat/vcs/**",
        "com/google/idea/sdkcompat/psi/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/java/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/clion/**",
            "com/google/idea/sdkcompat/javascript/**",
            "com/google/idea/sdkcompat/typescript/**",
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/javascript/**",
            "com/google/idea/sdkcompat/typescript/**",
            "com/google/idea/sdkcompat/scala/**",
        ]),
        intellij_ue = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/javascript/**",
            "com/google/idea/sdkcompat/typescript/**",
            "com/google/idea/sdkcompat/golang/**",
            "com/google/idea/sdkcompat/scala/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:coverage",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:junit",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/go",
        "//third_party/javascript",
        "//third_party/python",
        "//third_party/scala",
    ],
)
