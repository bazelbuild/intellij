# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])

java_library(
    name = "v203",
    srcs = glob([
        "com/google/idea/sdkcompat/general/**",
        "com/google/idea/sdkcompat/formatter/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cpp/**",
            "com/google/idea/sdkcompat/java/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/clion/**",
            "com/google/idea/sdkcompat/cpp/**",
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
        ]),
        intellij_ue = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/python",
        "//third_party/scala",
    ],
)
