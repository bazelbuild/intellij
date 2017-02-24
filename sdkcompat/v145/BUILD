# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

filegroup(
    name = "v145",
    srcs = glob([
        "com/google/idea/sdkcompat/codestyle/**",
        "com/google/idea/sdkcompat/smrunner/**",
        "com/google/idea/sdkcompat/transactions/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/debugger/**",
            "com/google/idea/sdkcompat/cidr/**",
        ]),
        clion = glob(["com/google/idea/sdkcompat/cidr/**"]),
        intellij = glob(["com/google/idea/sdkcompat/debugger/**"]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
)
