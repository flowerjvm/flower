package io.github.flowerjvm.flower.studio.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized common or legacy Flower observation event. */
public final class ObservationRecord {

    private final int schemaVersion;
    private final String source;
    private final String eventId;
    private final String eventType;
    private final String traceId;
    private final String runId;
    private final String parentRunId;
    private final String operationId;
    private final String operationName;
    private final long sequence;
    private final Instant occurredAt;
    private final Map<String, Object> attributes;

    public ObservationRecord(
            int schemaVersion,
            String source,
            String eventId,
            String eventType,
            String traceId,
            String runId,
            String parentRunId,
            String operationId,
            String operationName,
            long sequence,
            Instant occurredAt,
            Map<String, Object> attributes) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.source = requireText("source", source);
        this.eventId = requireText("eventId", eventId);
        this.eventType = requireText("eventType", eventType);
        this.traceId = requireText("traceId", traceId);
        this.runId = requireText("runId", runId);
        this.parentRunId = clean(parentRunId);
        this.operationId = clean(operationId);
        this.operationName = clean(operationName);
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        this.sequence = sequence;
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        this.occurredAt = occurredAt;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                attributes == null ? Collections.<String, Object>emptyMap() : attributes));
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String source() {
        return source;
    }

    public String eventId() {
        return eventId;
    }

    public String eventType() {
        return eventType;
    }

    public String traceId() {
        return traceId;
    }

    public String runId() {
        return runId;
    }

    public String parentRunId() {
        return parentRunId;
    }

    public String operationId() {
        return operationId;
    }

    public String operationName() {
        return operationName;
    }

    public long sequence() {
        return sequence;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    private static String requireText(String name, String value) {
        String selected = clean(value);
        if (selected == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return selected;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String selected = value.trim();
        return selected.isEmpty() ? null : selected;
    }
}
