load("@aspect_rules_lint//format:defs.bzl", "format_multirun")
load("@rules_java//java:defs.bzl", "java_binary")

package(default_visibility = ["//visibility:public"])

format_multirun(
    name = "format",
    java = ":java-format",
    kotlin = ":ktlint",
    protocol_buffer = "@rules_buf_toolchains//:buf",
    shell = "@aspect_rules_lint//format:shfmt",
    starlark = "@buildifier_prebuilt//:buildifier",
    yaml = "@aspect_rules_lint//format:yamlfmt",
)

java_binary(
    name = "java-format",
    jvm_flags = [
        "--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    ],
    main_class = "com.google.googlejavaformat.java.Main",
    runtime_deps = ["@google_java_format//jar"],
)

java_binary(
    name = "ktlint",
    args = ["--format"],
    main_class = "com.pinterest.ktlint.Main",
    runtime_deps = ["@ktlint//jar"],
)
