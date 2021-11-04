load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_binary")

package(default_visibility = ["//visibility:private"])

licenses(["notice"])

kt_jvm_binary(
    name = "run_project",
    srcs = ["main/Main.kt"],
    main_class = "main.Main",
    deps = ["@kotlinx_coroutines//jar"],
)
