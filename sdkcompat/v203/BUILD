# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])

java_library(
    name = "v203",
    srcs = glob([
        "com/google/idea/sdkcompat/general/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/java/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/clion/**",
            "com/google/idea/sdkcompat/typescript/**",
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/java/**",
        ]),
        intellij_ue = glob([
            "com/google/idea/sdkcompat/golang/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/typescript/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/go",
        "//third_party/javascript",
        "//third_party/python",
    ],
)
