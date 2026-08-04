package io.github.flowerjvm.flower.studio.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Event row rendered in the Studio timeline and inspector. */
public final class TimelineEventView {

    private final String eventId;
    private final String source;
    private final String eventType;
    private final String runId;
    private final String parentRunId;
    private final String operationId;
    private final String operationName;
    private final long sequence;
    private final String occurredAt;
    private final long relativeMillis;
    private final Long durationMillis;
    private final String category;
    private final String tone;
    private final String summary;
    private final Map<String, Object> attributes;
    private final List<ArtifactLinkView> artifacts;

    public TimelineEventView(
            String eventId,
            String source,
            String eventType,
            String runId,
            String parentRunId,
            String operationId,
            String operationName,
            long sequence,
            String occurredAt,
            long relativeMillis,
            Long durationMillis,
            String category,
            String tone,
            String summary,
            Map<String, Object> attributes,
            List<ArtifactLinkView> artifacts) {
        this.eventId = eventId;
        this.source = source;
        this.eventType = eventType;
        this.runId = runId;
        this.parentRunId = parentRunId;
        this.operationId = operationId;
        this.operationName = operationName;
        this.sequence = sequence;
        this.occurredAt = occurredAt;
        this.relativeMillis = relativeMillis;
        this.durationMillis = durationMillis;
        this.category = category;
        this.tone = tone;
        this.summary = summary;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
        this.artifacts = Collections.unmodifiableList(new ArrayList<ArtifactLinkView>(artifacts));
    }

    public String getEventId() {
        return eventId;
    }

    public String getSource() {
        return source;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRunId() {
        return runId;
    }

    public String getParentRunId() {
        return parentRunId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getOperationName() {
        return operationName;
    }

    public long getSequence() {
        return sequence;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public long getRelativeMillis() {
        return relativeMillis;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public String getCategory() {
        return category;
    }

    public String getTone() {
        return tone;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public List<ArtifactLinkView> getArtifacts() {
        return artifacts;
    }
}
