# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])

java_library(
    name = "v231",
    srcs = glob([
        "com/google/idea/sdkcompat/editor/**",
        "com/google/idea/sdkcompat/general/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/ui/icons/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cpp/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/kotlin/**",
            "com/google/idea/sdkcompat/as/refactoring/**",
            "com/google/idea/sdkcompat/fig/**",
            "com/google/idea/sdkcompat/psi/**",
            "com/google/idea/sdkcompat/vcs/**",
        ]),
        clion = ["com/google/idea/sdkcompat/refactoring/rename/DelegatingRenamePsiElementProcessorBase.java"] + glob([
            "com/google/idea/sdkcompat/cpp/**",
            "com/google/idea/sdkcompat/clion/**", # For classes incompatible with Android Studio
            "com/google/idea/sdkcompat/javascript/**", #api223
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
            "com/google/idea/sdkcompat/kotlin/**",
            "com/google/idea/sdkcompat/refactoring/**",
        ]),
        intellij_ue = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
            "com/google/idea/sdkcompat/kotlin/**",
            "com/google/idea/sdkcompat/javascript/**", #api223
            "com/google/idea/sdkcompat/refactoring/**",
            "com/google/idea/sdkcompat/go/**",  #api231
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
        "//third_party/go",  #api231
        "//third_party/scala",
        "//third_party/javascript" #api223
    ],
)
