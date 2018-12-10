licenses(["notice"])

java_plugin(
    name = "auto_annotation_plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = ["@auto_value//jar"],
)

java_plugin(
    name = "auto_oneof_plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = ["@auto_value//jar"],
)

java_plugin(
    name = "auto_value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    visibility = ["//visibility:public"],  # FastBuildCompilerFactoryImplTest uses this
    deps = ["@auto_value//jar"],
)

# provides both the jar for compilation and the java_plugin.
java_library(
    name = "auto_value",
    exported_plugins = [
        ":auto_annotation_plugin",
        ":auto_oneof_plugin",
        ":auto_value_plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto_value_annotations//jar"],
)
