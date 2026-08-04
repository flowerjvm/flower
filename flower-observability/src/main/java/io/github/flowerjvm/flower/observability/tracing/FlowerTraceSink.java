package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

/** Destination for standard Flower runtime trace events. */
@FunctionalInterface
public interface FlowerTraceSink {

    /**
     * Publish one event.
     *
     * <p>A sink connected directly to a Flower listener must return quickly.
     * Blocking file, database, HTTP, or messaging clients must be wrapped by
     * {@link AsyncFlowerTraceSink}.
     */
    void publish(FlowerTraceEvent event);

    static FlowerTraceSink noop() {
        return event -> {
        };
    }
}
