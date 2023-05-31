# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])

java_library(
    name = "v223",
    srcs = ["com/google/idea/sdkcompat/refactoring/rename/DelegatingRenamePsiElementProcessorBase.java"] + glob([
        "com/google/idea/sdkcompat/editor/**",
        "com/google/idea/sdkcompat/general/**",
        "com/google/idea/sdkcompat/fig/**",
        "com/google/idea/sdkcompat/logging/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/psi/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/vcs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/kotlin/**",
        ]) + ["com/google/idea/sdkcompat/refactoring/safedelete/JavaSafeDeleteProcessorCompat.java"],
        intellij = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
            "com/google/idea/sdkcompat/kotlin/**",
        ]) + ["com/google/idea/sdkcompat/refactoring/safedelete/JavaSafeDeleteProcessorCompat.java"],
        intellij_ue = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
            "com/google/idea/sdkcompat/kotlin/**",
        ]) + ["com/google/idea/sdkcompat/refactoring/safedelete/JavaSafeDeleteProcessorCompat.java"],
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
        "//third_party/scala",
    ],
)
