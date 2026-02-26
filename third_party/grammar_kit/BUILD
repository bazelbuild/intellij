load("@rules_java//java:java_binary.bzl", "java_binary")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

exports_files(["idea-flex.skeleton"])

kt_jvm_library(
    name = "generator",
    srcs = [":main.kt"],
    visibility = ["//visibility:private"],
    deps = [
        "//intellij_platform_sdk:grammar_kit_libs",
        "@maven//:org_jetbrains_grammar_kit",
    ],
)

java_binary(
    name = "grammar_kit",
    main_class = "com.google.idea.grammar_kit.MainKt",
    visibility = ["//visibility:public"],
    runtime_deps = [":generator"],
)

java_binary(
    name = "jflex",
    main_class = "jflex.Main",
    visibility = ["//visibility:public"],
    runtime_deps = ["@maven//:org_jetbrains_intellij_deps_jflex_jflex"],
)
