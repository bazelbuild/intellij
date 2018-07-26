# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])  # Apache 2.0

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

java_library(
    name = "v171",
    srcs = glob([
        "com/google/idea/sdkcompat/codestyle/**",
        "com/google/idea/sdkcompat/plugin/**",
        "com/google/idea/sdkcompat/psi/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/query/**",
        "com/google/idea/sdkcompat/run/**",
        "com/google/idea/sdkcompat/smrunner/**",
        "com/google/idea/sdkcompat/ui/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/kotlin/**",
            "studio/**",
        ]),
        clion = glob([
            "com/google/idea/sdkcompat/cidr/**",
            "com/google/idea/sdkcompat/clion/**",
        ]),
        intellij = glob([
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
        "//third_party/python",
    ],
)

filegroup(
    name = "plugin_xml",
    srcs = ["META_INF/sdkcompat.xml"],
    visibility = ["//sdkcompat:__pkg__"],
)
