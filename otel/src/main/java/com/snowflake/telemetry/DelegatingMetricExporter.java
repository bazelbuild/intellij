package com.snowflake.telemetry;

import com.intellij.openapi.diagnostic.Logger;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;

/**
 * A MetricExporter implementation that delegates all operations to an underlying
 * OtlpGrpcMetricExporter. This wrapper allows for additional customization or monitoring of the
 * export process if needed.
 */
public class DelegatingMetricExporter implements MetricExporter {
  private static final Logger LOG = Logger.getInstance(DelegatingMetricExporter.class);
  private final OtlpGrpcMetricExporter delegate;
  private final String metricPrefix;

  /**
   * Creates a new DelegatingMetricExporter.
   *
   * @param delegate the underlying OtlpGrpcMetricExporter that will handle the actual export
   * @throws NullPointerException if delegate is null
   */
  public DelegatingMetricExporter(OtlpGrpcMetricExporter delegate, String prefix) {
    this.delegate = delegate;
    this.metricPrefix = prefix;
  }

  @Override
  public Aggregation getDefaultAggregation(InstrumentType instrumentType) {
    return delegate.getDefaultAggregation(instrumentType);
  }

  @Override
  public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
    return delegate.getAggregationTemporality(instrumentType);
  }

  @Override
  public MemoryMode getMemoryMode() {
    return delegate.getMemoryMode();
  }

  @Override
  public CompletableResultCode export(Collection<MetricData> metrics) {
    Collection<MetricData> prefixedMetrics =
        metrics.stream().map(m -> PrefixedMetricData.create(m, metricPrefix)).toList();
    return delegate.export(prefixedMetrics);
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public CompletableResultCode shutdown() {
    return delegate.shutdown();
  }

  /**
   * Returns the underlying OtlpGrpcMetricExporter.
   *
   * @return the delegate exporter
   */
  public OtlpGrpcMetricExporter getDelegate() {
    return delegate;
  }
}
