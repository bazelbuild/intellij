# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])

java_library(
    name = "v212",
    srcs = glob([
        "com/google/idea/sdkcompat/indexing/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cpp/**",
            "com/google/idea/sdkcompat/java/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/cpp/**",
            "com/google/idea/sdkcompat/clion/**",
            "com/google/idea/sdkcompat/typescript/**",
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
        ]),
        intellij_ue = glob([
            "com/google/idea/sdkcompat/golang/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
            "com/google/idea/sdkcompat/typescript/**",
        ]),
    ) + select_for_ide(
        # TODO(b/215594482): Studio is on 2021.2.3, but IJ is on 2021.2.4
        # We need separate compat APIs for these two versions until Studio
        # is updated to 2021.2.4.
        android_studio = glob([
            "com/google/idea/sdkcompat/studio/**",
        ]),
        default = glob([
            "com/google/idea/sdkcompat/general/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/go",
        "//third_party/javascript",
        "//third_party/python",
        "//third_party/scala",
    ],
)
