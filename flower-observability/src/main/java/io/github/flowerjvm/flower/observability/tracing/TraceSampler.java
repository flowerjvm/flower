package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

/** Selects whether a complete logical Flower trace should be retained. */
@FunctionalInterface
public interface TraceSampler {

    boolean sample(FlowerTraceEvent event);
}
