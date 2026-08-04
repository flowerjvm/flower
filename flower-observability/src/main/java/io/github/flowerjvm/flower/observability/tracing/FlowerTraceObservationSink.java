package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceAttributes;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts Core {@link FlowerTraceEvent} records to the common envelope. */
public final class FlowerTraceObservationSink implements FlowerTraceSink {

    public static final String SOURCE = "flower-core";
    public static final String FLOW_TYPE = "flower.flow.type";
    public static final String FLOW_KEY = "flower.flow.key";
    public static final String WORKER_NAME = "flower.worker.name";

    private final FlowerObservationSink destination;

    public FlowerTraceObservationSink(FlowerObservationSink destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        this.destination = destination;
    }

    @Override
    public void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        Map<String, Object> attributes = new LinkedHashMap<>(event.attributes());
        attributes.put(FLOW_TYPE, event.flowId().flowType());
        attributes.put(FLOW_KEY, event.flowId().flowKey());
        attributes.put(WORKER_NAME, event.workerName());
        Object stepId = event.attributes().get(FlowerTraceAttributes.STEP_ID);

        destination.publish(FlowerObservationEvent.builder(SOURCE, event.type().name())
                .eventId(event.eventId())
                .traceId(event.traceId())
                .runId(event.flowRunId())
                .parentRunId(event.parentRunId())
                .operationId(event.stepRunId())
                .operationName(stepId == null ? null : String.valueOf(stepId))
                .sequence(event.sequence())
                .occurredAt(event.occurredAt())
                .attributes(attributes)
                .build());
    }
}
