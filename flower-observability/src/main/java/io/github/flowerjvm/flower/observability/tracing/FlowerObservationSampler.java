package io.github.flowerjvm.flower.observability.tracing;

/** Selects whether a complete correlated observation trace is retained. */
@FunctionalInterface
public interface FlowerObservationSampler {

    boolean sample(FlowerObservationEvent event);
}
