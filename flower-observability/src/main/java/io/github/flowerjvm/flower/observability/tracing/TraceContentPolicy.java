package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

/** Selects how explicitly opted-in trace content is retained. */
@FunctionalInterface
public interface TraceContentPolicy {

    Decision decide(
            FlowerTraceEvent event,
            String attributeName,
            TraceContent content);

    enum Decision {
        DROP,
        INLINE,
        ARTIFACT
    }
}
