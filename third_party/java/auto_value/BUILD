load("@rules_java//java:java_library.bzl", "java_library")
load("@rules_java//java:java_plugin.bzl", "java_plugin")

licenses(["notice"])

java_plugin(
    name = "autovalue-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@maven//:com_google_auto_value_auto_value"],
)

java_plugin(
    name = "autobuilder-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoBuilderProcessor",
    deps = ["@maven//:com_google_auto_value_auto_value"],
)

java_plugin(
    name = "auto_annotation_plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = ["@maven//:com_google_auto_value_auto_value"],
)

java_plugin(
    name = "autooneof-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = ["@maven//:com_google_auto_value_auto_value"],
)

# provides both the jar for compilation and the java_plugin.
java_library(
    name = "auto_value",
    exported_plugins = [
        ":autovalue-plugin",
        ":autobuilder-plugin",
        ":autooneof-plugin",
        ":auto_annotation_plugin",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:com_google_auto_value_auto_value",
        "@maven//:com_google_auto_value_auto_value_annotations",
        "@maven//:org_jspecify_jspecify",
    ],
)
