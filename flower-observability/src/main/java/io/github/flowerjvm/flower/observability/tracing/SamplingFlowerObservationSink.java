package io.github.flowerjvm.flower.observability.tracing;

import java.util.concurrent.atomic.AtomicLong;

/** Applies a trace-level sample decision to common observation events. */
public final class SamplingFlowerObservationSink implements FlowerObservationSink {

    private final FlowerObservationSink delegate;
    private final FlowerObservationSampler sampler;
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public SamplingFlowerObservationSink(
            FlowerObservationSink delegate,
            FlowerObservationSampler sampler) {
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
    public void publish(FlowerObservationEvent event) {
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
