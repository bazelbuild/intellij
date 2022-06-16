package com.google.idea.blaze.base.analytics;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import java.util.Objects;

public final class OtlpTraceSpan implements TraceSpan {

    private static final String SCOPE_NAME = "sync";
    private final Span span;

    private OtlpTraceSpan(Span span) {
        Objects.requireNonNull(span);
        this.span = span;
    }

    public static TraceSpan create(String name) {
        Tracer tracer = OtlpTracer.create(SCOPE_NAME);
        Span span = tracer
                .spanBuilder(name)
                .startSpan();
        return new OtlpTraceSpan(span);
    }

    @Override
    public void close() {
        span.end();
    }
}
