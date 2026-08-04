package io.github.flowerjvm.flower.observability.tracing;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, payload-light envelope shared by Flower and domain observation
 * adapters.
 *
 * <p>The source owns the meaning of {@code eventType}. This envelope supplies
 * correlation and storage fields without moving Agent, Harness, Action, or
 * host event enums into Flower Core.
 */
public final class FlowerObservationEvent {

    public static final int CURRENT_SCHEMA_VERSION = 1;

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

    private FlowerObservationEvent(Builder builder) {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.source = requireText("source", builder.source);
        this.eventId = requireText("eventId", builder.eventId);
        this.eventType = requireText("eventType", builder.eventType);
        this.traceId = requireText("traceId", builder.traceId);
        this.runId = requireText("runId", builder.runId);
        this.parentRunId = clean(builder.parentRunId);
        this.operationId = clean(builder.operationId);
        this.operationName = clean(builder.operationName);
        if (builder.sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative: " + builder.sequence);
        }
        this.sequence = builder.sequence;
        if (builder.occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        this.occurredAt = builder.occurredAt;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static Builder builder(String source, String eventType) {
        return new Builder(source, eventType);
    }

    public Builder toBuilder() {
        return new Builder(source, eventType)
                .eventId(eventId)
                .traceId(traceId)
                .runId(runId)
                .parentRunId(parentRunId)
                .operationId(operationId)
                .operationName(operationName)
                .sequence(sequence)
                .occurredAt(occurredAt)
                .attributes(attributes);
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

    @Override
    public String toString() {
        return "FlowerObservationEvent{" + source + ":" + eventType
                + " run=" + runId + " seq=" + sequence + "}";
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

    public static final class Builder {
        private final String source;
        private final String eventType;
        private String eventId;
        private String traceId;
        private String runId;
        private String parentRunId;
        private String operationId;
        private String operationName;
        private long sequence;
        private Instant occurredAt;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(String source, String eventType) {
            this.source = source;
            this.eventType = eventType;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder parentRunId(String parentRunId) {
            this.parentRunId = parentRunId;
            return this;
        }

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder sequence(long sequence) {
            this.sequence = sequence;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder attribute(String name, Object value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("attribute name must not be blank");
            }
            if (value != null) {
                attributes.put(name.trim(), value);
            }
            return this;
        }

        public Builder attributes(Map<String, ?> values) {
            if (values != null) {
                for (Map.Entry<String, ?> entry : values.entrySet()) {
                    attribute(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        Builder clearAttributes() {
            attributes.clear();
            return this;
        }

        public FlowerObservationEvent build() {
            return new FlowerObservationEvent(this);
        }
    }
}
