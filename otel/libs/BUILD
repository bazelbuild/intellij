load("@rules_java//java:java_library.bzl", "java_library")
load("@rules_java//java:java_import.bzl", "java_import")

package(default_visibility = ["//otel:__subpackages__"])

java_library(
    name = "opentelemetry-exporter-libs",
    visibility = ["//visibility:public"],
    exports = [
        ":opentelemetry-exporter-otlp-common-1_47_0_http_import",
        ":opentelemetry-exporter-common-1_47_0_http_import",
        ":opentelemetry-exporter-otlp-1_47_0_http_import",
        ":opentelemetry-sdk-1_47_0_http_import",
        ":opentelemetry-sdk-extension-autoconfigure-spi-1_47_0_http_import",
        ":opentelemetry-exporter-sender-grpc-managed-channel-1_47_0_http_import",
    ],
)

java_import(
    name = "opentelemetry-exporter-otlp-common-1_47_0_http_import",
    jars = ["@opentelemetry-exporter-otlp-common-1_47_0_http//file"],
)

java_import(
    name = "opentelemetry-exporter-common-1_47_0_http_import",
    jars = ["@opentelemetry-exporter-common-1_47_0_http//file"],
)

java_import(
    name = "opentelemetry-exporter-otlp-1_47_0_http_import",
    jars = ["@opentelemetry-exporter-otlp-1_47_0_http//file"],
)

java_import(
    name = "opentelemetry-exporter-sender-grpc-managed-channel-1_47_0_http_import",
    jars = ["@opentelemetry-exporter-sender-grpc-managed-channel-1_47_0_http//file"],
)

java_import(
    name = "opentelemetry-sdk-1_47_0_http_import",
    jars = ["@opentelemetry-sdk-1_47_0_http//file"],
)

java_import(
    name = "opentelemetry-sdk-extension-autoconfigure-spi-1_47_0_http_import",
    jars = ["@opentelemetry-sdk-extension-autoconfigure-spi-1_47_0_http//file"],
)
