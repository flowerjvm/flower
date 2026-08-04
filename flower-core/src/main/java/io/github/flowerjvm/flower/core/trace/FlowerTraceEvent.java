package io.github.flowerjvm.flower.core.trace;

import io.github.flowerjvm.flower.core.flow.FlowId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, payload-light description of one Flower runtime fact.
 *
 * <p>Events contain orchestration metadata only. Business payloads, model
 * prompts, Tool results, and secrets must be captured by opt-in domain
 * instrumentation and sanitization outside Flower core.
 */
public final class FlowerTraceEvent {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String SOURCE = "flower-core";

    private final int schemaVersion;
    private final String eventId;
    private final FlowerTraceEventType type;
    private final String traceId;
    private final String flowRunId;
    private final String stepRunId;
    private final String parentRunId;
    private final FlowId flowId;
    private final String workerName;
    private final long sequence;
    private final Instant occurredAt;
    private final Map<String, Object> attributes;

    private FlowerTraceEvent(Builder builder) {
        if (builder.eventId == null || builder.eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (builder.type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (builder.traceId == null || builder.traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (builder.flowRunId == null || builder.flowRunId.trim().isEmpty()) {
            throw new IllegalArgumentException("flowRunId must not be blank");
        }
        if (builder.flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        if (builder.workerName == null || builder.workerName.trim().isEmpty()) {
            throw new IllegalArgumentException("workerName must not be blank");
        }
        if (builder.sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive: " + builder.sequence);
        }
        if (builder.occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.eventId = builder.eventId;
        this.type = builder.type;
        this.traceId = builder.traceId;
        this.flowRunId = builder.flowRunId;
        this.stepRunId = clean(builder.stepRunId);
        this.parentRunId = clean(builder.parentRunId);
        this.flowId = builder.flowId;
        this.workerName = builder.workerName;
        this.sequence = builder.sequence;
        this.occurredAt = builder.occurredAt;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static Builder builder(FlowerTraceEventType type) {
        return new Builder(type);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String source() {
        return SOURCE;
    }

    public String eventId() {
        return eventId;
    }

    public FlowerTraceEventType type() {
        return type;
    }

    public String traceId() {
        return traceId;
    }

    public String flowRunId() {
        return flowRunId;
    }

    public String stepRunId() {
        return stepRunId;
    }

    public String parentRunId() {
        return parentRunId;
    }

    public FlowId flowId() {
        return flowId;
    }

    public String workerName() {
        return workerName;
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
        return "FlowerTraceEvent{" + type + " " + flowId
                + " run=" + flowRunId + " seq=" + sequence
                + (stepRunId != null ? " stepRun=" + stepRunId : "")
                + "}";
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static final class Builder {
        private final FlowerTraceEventType type;
        private String eventId;
        private String traceId;
        private String flowRunId;
        private String stepRunId;
        private String parentRunId;
        private FlowId flowId;
        private String workerName;
        private long sequence;
        private Instant occurredAt;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(FlowerTraceEventType type) {
            this.type = type;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder flowRunId(String flowRunId) {
            this.flowRunId = flowRunId;
            return this;
        }

        public Builder stepRunId(String stepRunId) {
            this.stepRunId = stepRunId;
            return this;
        }

        public Builder parentRunId(String parentRunId) {
            this.parentRunId = parentRunId;
            return this;
        }

        public Builder flowId(FlowId flowId) {
            this.flowId = flowId;
            return this;
        }

        public Builder workerName(String workerName) {
            this.workerName = workerName;
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
                attributes.put(name, value);
            }
            return this;
        }

        public Builder attributes(Map<String, ?> values) {
            if (values == null) {
                return this;
            }
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                attribute(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public FlowerTraceEvent build() {
            return new FlowerTraceEvent(this);
        }
    }
}
