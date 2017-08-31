licenses(["notice"])

java_plugin(
    name = "autovalue-plugin",
    generates_api = 1,
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@auto_value//jar"],
)

# provides both the jar for compilation and the java_plugin.
java_library(
    name = "auto_value",
    exported_plugins = [":autovalue-plugin"],
    visibility = ["//visibility:public"],
    exports = ["@auto_value//jar"],
)
