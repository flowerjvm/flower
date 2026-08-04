package io.github.flowerjvm.flower.observability.tracing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Publishes each common observation event to multiple sinks in order. */
public final class CompositeFlowerObservationSink implements FlowerObservationSink {

    private final List<FlowerObservationSink> sinks;

    public CompositeFlowerObservationSink(List<? extends FlowerObservationSink> sinks) {
        if (sinks == null) {
            throw new IllegalArgumentException("sinks must not be null");
        }
        List<FlowerObservationSink> copy = new ArrayList<>(sinks.size());
        for (FlowerObservationSink sink : sinks) {
            if (sink == null) {
                throw new IllegalArgumentException("sinks must not contain null");
            }
            copy.add(sink);
        }
        this.sinks = Collections.unmodifiableList(copy);
    }

    public static CompositeFlowerObservationSink of(FlowerObservationSink... sinks) {
        if (sinks == null) {
            throw new IllegalArgumentException("sinks must not be null");
        }
        return new CompositeFlowerObservationSink(Arrays.asList(sinks));
    }

    @Override
    public void publish(FlowerObservationEvent event) {
        RuntimeException firstFailure = null;
        for (FlowerObservationSink sink : sinks) {
            try {
                sink.publish(event);
            } catch (RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
