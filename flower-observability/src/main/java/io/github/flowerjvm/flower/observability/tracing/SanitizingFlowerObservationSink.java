package io.github.flowerjvm.flower.observability.tracing;

import java.util.concurrent.atomic.AtomicLong;

/** Fail-closed sanitizing wrapper for common observation events. */
public final class SanitizingFlowerObservationSink implements FlowerObservationSink {

    private final FlowerObservationSink delegate;
    private final FlowerObservationSanitizer sanitizer;
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public SanitizingFlowerObservationSink(
            FlowerObservationSink delegate,
            FlowerObservationSanitizer sanitizer) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (sanitizer == null) {
            throw new IllegalArgumentException("sanitizer must not be null");
        }
        this.delegate = delegate;
        this.sanitizer = sanitizer;
    }

    @Override
    public void publish(FlowerObservationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        FlowerObservationEvent sanitized;
        try {
            sanitized = sanitizer.sanitize(event);
        } catch (RuntimeException failure) {
            failures.incrementAndGet();
            dropped.incrementAndGet();
            return;
        }
        if (sanitized == null) {
            failures.incrementAndGet();
            dropped.incrementAndGet();
            return;
        }
        delegate.publish(sanitized);
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
