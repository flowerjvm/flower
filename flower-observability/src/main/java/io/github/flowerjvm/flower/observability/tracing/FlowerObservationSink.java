package io.github.flowerjvm.flower.observability.tracing;

/** Destination shared by Core and domain observation adapters. */
@FunctionalInterface
public interface FlowerObservationSink {

    /**
     * Publish one observation event.
     *
     * <p>A sink invoked directly by a runtime hook must return quickly. Wrap
     * blocking storage or network work with {@link AsyncFlowerObservationSink}.
     */
    void publish(FlowerObservationEvent event);

    static FlowerObservationSink noop() {
        return event -> {
        };
    }
}
