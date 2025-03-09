package com.snowflake.telemetry;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.platform.diagnostic.telemetry.impl.OpenTelemetryExporterProvider;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SnowflakeOtelExporterProvider implements OpenTelemetryExporterProvider {
    private static final Logger LOG = Logger.getInstance(SnowflakeOtelExporterProvider.class);

    @Override
    public List<MetricExporter> getMetricsExporters() {
        boolean enabled = Boolean.parseBoolean(System.getProperty("sf.otlp.exporter.enabled", "false"));
        if (!enabled) {
            return Collections.emptyList();
        }

        return Collections.singletonList(createMetricExporter());
    }

    private MetricExporter createMetricExporter() {
        String endpoint = "http://localhost:" + System.getProperty("idea.diagnostic.opentelemetry.otlp", "4317") + "/v1/metrics";
        String metricNamePrefix = System.getProperty("sf.otlp.metric.prefix", "sf_ide");
        LOG.warn("Creating OTLP HTTP metric exporter with endpoint: " + endpoint + " and metric prefix: " + metricNamePrefix);
        
        return new DelegatingMetricExporter(OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(5, TimeUnit.SECONDS)
            .build(), metricNamePrefix);
    }
}
