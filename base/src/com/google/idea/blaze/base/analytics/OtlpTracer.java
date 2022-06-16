package com.google.idea.blaze.base.analytics;

import java.io.IOException;
import java.net.Socket;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

public final class OtlpTracer {

    private static final String OTLP_HOST = "localhost";
    private static final int OTLP_PORT = 4317;
    private static final String SERVICE_NAME = "canva.devenv.bazelbuild-intellij";

    // If we cannot open the specified port, we assume that
    // opentelemetry-collector is not running and no-op the implementaton.
    private static OpenTelemetry openTelemetry = isSocketListening(OTLP_HOST, OTLP_PORT)
            ? initializeOpenTelemetrySdk(SERVICE_NAME, OTLP_HOST, OTLP_PORT)
            : OpenTelemetry.noop();

    public static Tracer create(String scopeName) {
        return openTelemetry.getTracer(scopeName);
    }

    private static OpenTelemetry initializeOpenTelemetrySdk(String serviceName, String host, int port) {
        Attributes attributes = Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .build();

        Resource serviceResource = Resource
                .create(attributes);

        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpointAsString(host, port))
                .build();

        SpanProcessor processor = SimpleSpanProcessor.create(exporter);

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(processor)
                .setResource(serviceResource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    private static boolean isSocketListening(String host, int port) {
        try (Socket ignored = new Socket(host, port)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String endpointAsString(String host, int port) {
        return "http://" + host + ":" + port;
    }
}