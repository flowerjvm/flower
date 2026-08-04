package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceListener;

/** Adapts the core {@link FlowerTraceListener} callback to a sink. */
public final class FlowerTraceSinkListener implements FlowerTraceListener {

    private final FlowerTraceSink sink;

    public FlowerTraceSinkListener(FlowerTraceSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
    }

    @Override
    public void onTraceEvent(FlowerTraceEvent event) {
        sink.publish(event);
    }
}
