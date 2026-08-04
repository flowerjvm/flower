package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.Map;

final class FlowerTraceEvents {

    private FlowerTraceEvents() {
    }

    static FlowerTraceEvent withAttributes(
            FlowerTraceEvent source,
            Map<String, ?> attributes) {
        return FlowerTraceEvent.builder(source.type())
                .eventId(source.eventId())
                .traceId(source.traceId())
                .flowRunId(source.flowRunId())
                .stepRunId(source.stepRunId())
                .parentRunId(source.parentRunId())
                .flowId(source.flowId())
                .workerName(source.workerName())
                .sequence(source.sequence())
                .occurredAt(source.occurredAt())
                .attributes(attributes)
                .build();
    }
}
