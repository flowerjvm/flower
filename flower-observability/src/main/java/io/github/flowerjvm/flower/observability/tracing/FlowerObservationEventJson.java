package io.github.flowerjvm.flower.observability.tracing;

import java.util.LinkedHashMap;
import java.util.Map;

final class FlowerObservationEventJson {

    private FlowerObservationEventJson() {
    }

    static String toJson(FlowerObservationEvent event) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", event.schemaVersion());
        document.put("source", event.source());
        document.put("eventId", event.eventId());
        document.put("eventType", event.eventType());
        document.put("traceId", event.traceId());
        document.put("runId", event.runId());
        document.put("parentRunId", event.parentRunId());
        document.put("operationId", event.operationId());
        document.put("operationName", event.operationName());
        document.put("sequence", event.sequence());
        document.put("occurredAt", event.occurredAt().toString());
        document.put("attributes", event.attributes());
        return FlowerTraceEventJson.encodeDocument(document);
    }
}
