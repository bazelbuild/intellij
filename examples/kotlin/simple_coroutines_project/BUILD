load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_binary", "kt_jvm_library", "kt_jvm_test")

package(default_visibility = ["//visibility:private"])

licenses(["notice"])

kt_jvm_library(
    name = "greeting_lib",
    srcs = ["main/lib/Greeting.kt"],
    deps = ["@kotlinx_coroutines//jar"],
)

kt_jvm_binary(
    name = "run_project",
    srcs = ["main/Main.kt"],
    main_class = "main.Main",
    deps = [
        ":greeting_lib",
        "@kotlinx_coroutines//jar",
    ],
)

kt_jvm_test(
    name = "test_greeting",
    srcs = ["test/GreetingTest.kt"],
    test_class = "test.GreetingTest",
    deps = [
        ":greeting_lib",
        "@junit//jar",
        "@kotlinx_coroutines//jar",
        "@kotlinx_coroutines_test//jar",
        "@truth//jar",
    ],
)
