package io.github.flowerjvm.flower.observability.tracing;

/** Fast, non-blocking sanitizer for common observation events. */
@FunctionalInterface
public interface FlowerObservationSanitizer {

    FlowerObservationEvent sanitize(FlowerObservationEvent event);

    static FlowerObservationSanitizer noop() {
        return event -> event;
    }
}
