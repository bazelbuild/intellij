java_library(
    name = "exports",
    srcs = ["TestClassUsingExport.java"],
    deps = [":exported-collect"],
)

java_library(
    name = "exported-collect",
    exports = ["//java/com/google/common/collect"],
)
