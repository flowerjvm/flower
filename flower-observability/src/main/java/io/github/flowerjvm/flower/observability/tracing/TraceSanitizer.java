package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

/**
 * Removes or replaces sensitive data before a trace event leaves the Worker
 * thread.
 *
 * <p>Implementations must be deterministic, quick, and free of blocking I/O.
 * They should preserve event identity and correlation fields. Return a
 * non-null event or throw when sanitization cannot be completed safely.
 */
@FunctionalInterface
public interface TraceSanitizer {

    FlowerTraceEvent sanitize(FlowerTraceEvent event);

    static TraceSanitizer noop() {
        return event -> event;
    }
}
