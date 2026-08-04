package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Applies a {@link TraceSanitizer} before forwarding an event.
 *
 * <p>Sanitization is fail-closed. A runtime sanitization failure or null result
 * drops the event and never exposes the original event to the delegate.
 */
public final class SanitizingFlowerTraceSink implements FlowerTraceSink {

    private final FlowerTraceSink delegate;
    private final TraceSanitizer sanitizer;
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public SanitizingFlowerTraceSink(
            FlowerTraceSink delegate,
            TraceSanitizer sanitizer) {
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
    public void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        FlowerTraceEvent sanitized;
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
