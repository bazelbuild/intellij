licenses(["notice"])

java_plugin(
    name = "autovalue-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@auto_value//jar"],
)

java_plugin(
    name = "autobuilder-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoBuilderProcessor",
    deps = ["@auto_value//jar"],
)

java_plugin(
    name = "auto_annotation_plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = ["@auto_value//jar"],
)

java_plugin(
    name = "autooneof-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = ["@auto_value//jar"],
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
        "@auto_value//jar",
        "@auto_value_annotations//jar",
    ],
)
