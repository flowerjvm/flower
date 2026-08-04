package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

/**
 * Stores content outside the event and returns a portable reference.
 *
 * <p>Implementations may perform blocking I/O and therefore belong behind an
 * {@link AsyncFlowerTraceSink}, never directly on a Flower Worker thread.
 */
@FunctionalInterface
public interface TraceArtifactStore {

    TraceArtifactReference store(
            FlowerTraceEvent event,
            String attributeName,
            TraceContent content);
}
