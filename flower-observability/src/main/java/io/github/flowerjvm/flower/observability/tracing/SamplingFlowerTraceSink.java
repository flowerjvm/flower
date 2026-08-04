package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.concurrent.atomic.AtomicLong;

/** Applies a trace-level sampling decision before publishing. */
public final class SamplingFlowerTraceSink implements FlowerTraceSink {

    private final FlowerTraceSink delegate;
    private final TraceSampler sampler;
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public SamplingFlowerTraceSink(FlowerTraceSink delegate, TraceSampler sampler) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (sampler == null) {
            throw new IllegalArgumentException("sampler must not be null");
        }
        this.delegate = delegate;
        this.sampler = sampler;
    }

    @Override
    public void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        boolean selected;
        try {
            selected = sampler.sample(event);
        } catch (RuntimeException failure) {
            failures.incrementAndGet();
            dropped.incrementAndGet();
            return;
        }
        if (!selected) {
            dropped.incrementAndGet();
            return;
        }
        delegate.publish(event);
        published.incrementAndGet();
    }

    public long publishedCount() {
        return published.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long failureCount() {
        return failures.get();
    }
}
