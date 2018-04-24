# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

java_library(
    name = "v181",
    srcs = glob([
        "com/google/idea/sdkcompat/codestyle/**",
        "com/google/idea/sdkcompat/plugin/**",
        "com/google/idea/sdkcompat/psi/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/run/**",
        "com/google/idea/sdkcompat/smrunner/**",
        "com/google/idea/sdkcompat/transactions/**",
        "com/google/idea/sdkcompat/ui/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/kotlin/**",
            "studio/com/google/idea/sdkcompat/cidr/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/clion/**",
            "com/google/idea/sdkcompat/cidr/**",
            "clion/com/google/idea/sdkcompat/cidr/**",
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/golang/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/kotlin/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:junit",
        "//intellij_platform_sdk:kotlin",
        "//intellij_platform_sdk:plugin_api",
        "//third_party/go",
        "//third_party/python",
    ],
)
