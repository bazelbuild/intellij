package com.snowflake.telemetry;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.Data;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.resources.Resource;

/**
 * A wrapper class that implements the MetricData interface prefixing the name of the underlying data
 * and by delegating all other operations to the underlying MetricData instance.
 * This allows for customization of metric data withouth needing to use reflection to modify
 * or create instances of MetricData which are not exported by the SDK.
 */
public class PrefixedMetricData implements MetricData {
    private final MetricData delegate;
    private final String prefix;

    PrefixedMetricData(MetricData delegate, String prefix) {
        this.delegate = delegate;
        this.prefix = prefix;
    }

    public static MetricData create(MetricData delegate, String prefix) {
        return new PrefixedMetricData(delegate, prefix);
    }

    @Override
    public String getName() {
        return prefix + "." + delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public String getUnit() {
        return delegate.getUnit();
    }

    @Override
    public MetricDataType getType() {
        return delegate.getType();
    }

    @Override
    public Data<?> getData() {
        return delegate.getData();
    }

    @Override
    public Resource getResource() {
        return delegate.getResource();
    }

    @Override
    public InstrumentationScopeInfo getInstrumentationScopeInfo() {
        return delegate.getInstrumentationScopeInfo();
    }
}
