package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Publishes each event to multiple sinks in declaration order. */
public final class CompositeFlowerTraceSink implements FlowerTraceSink {

    private final List<FlowerTraceSink> sinks;

    public CompositeFlowerTraceSink(List<? extends FlowerTraceSink> sinks) {
        if (sinks == null) {
            throw new IllegalArgumentException("sinks must not be null");
        }
        List<FlowerTraceSink> copy = new ArrayList<>(sinks.size());
        for (FlowerTraceSink sink : sinks) {
            if (sink == null) {
                throw new IllegalArgumentException("sinks must not contain null");
            }
            copy.add(sink);
        }
        this.sinks = Collections.unmodifiableList(copy);
    }

    public static CompositeFlowerTraceSink of(FlowerTraceSink... sinks) {
        if (sinks == null) {
            throw new IllegalArgumentException("sinks must not be null");
        }
        return new CompositeFlowerTraceSink(Arrays.asList(sinks));
    }

    @Override
    public void publish(FlowerTraceEvent event) {
        RuntimeException firstFailure = null;
        for (FlowerTraceSink sink : sinks) {
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
