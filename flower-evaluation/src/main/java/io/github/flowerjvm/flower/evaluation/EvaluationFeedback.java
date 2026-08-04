package io.github.flowerjvm.flower.evaluation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable human feedback record for an experiment or one evaluated example. */
public final class EvaluationFeedback {

    public static final int SCHEMA_VERSION = 1;
    public static final String RECORD_TYPE = "FEEDBACK";

    private final int schemaVersion;
    private final String feedbackId;
    private final String experimentId;
    private final String exampleId;
    private final String traceId;
    private final FeedbackRating rating;
    private final Set<String> labels;
    private final String comment;
    private final String createdAt;

    @JsonCreator
    public EvaluationFeedback(
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("feedbackId") String feedbackId,
            @JsonProperty("experimentId") String experimentId,
            @JsonProperty("exampleId") String exampleId,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("rating") FeedbackRating rating,
            @JsonProperty("labels") Collection<String> labels,
            @JsonProperty("comment") String comment,
            @JsonProperty("createdAt") String createdAt) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.feedbackId = EvaluationValues.requireText("feedbackId", feedbackId);
        this.experimentId = EvaluationValues.requireText("experimentId", experimentId);
        this.exampleId = EvaluationValues.cleanText(exampleId);
        this.traceId = EvaluationValues.cleanText(traceId);
        if (rating == null) {
            throw new IllegalArgumentException("rating must not be null");
        }
        this.rating = rating;
        this.labels = EvaluationValues.immutableTextSet(labels);
        this.comment = EvaluationValues.cleanText(comment);
        String selectedCreatedAt = EvaluationValues.requireText("createdAt", createdAt);
        Instant.parse(selectedCreatedAt);
        this.createdAt = selectedCreatedAt;
    }

    public static Builder builder(String feedbackId, String experimentId, FeedbackRating rating) {
        return new Builder(feedbackId, experimentId, rating);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getRecordType() {
        return RECORD_TYPE;
    }

    public String getFeedbackId() {
        return feedbackId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getExampleId() {
        return exampleId;
    }

    public String getTraceId() {
        return traceId;
    }

    public FeedbackRating getRating() {
        return rating;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public String getComment() {
        return comment;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public static final class Builder {
        private final String feedbackId;
        private final String experimentId;
        private final FeedbackRating rating;
        private String exampleId;
        private String traceId;
        private final Set<String> labels = new LinkedHashSet<String>();
        private String comment;
        private Instant createdAt;

        private Builder(String feedbackId, String experimentId, FeedbackRating rating) {
            this.feedbackId = feedbackId;
            this.experimentId = experimentId;
            this.rating = rating;
        }

        public Builder exampleId(String exampleId) {
            this.exampleId = exampleId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder label(String label) {
            labels.add(EvaluationValues.requireText("label", label));
            return this;
        }

        /** Comments are opt-in content and should be sanitized before persistence. */
        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            if (createdAt == null) {
                throw new IllegalArgumentException("createdAt must not be null");
            }
            this.createdAt = createdAt;
            return this;
        }

        public EvaluationFeedback build() {
            if (createdAt == null) {
                throw new IllegalStateException("createdAt must be set");
            }
            return new EvaluationFeedback(
                    SCHEMA_VERSION,
                    feedbackId,
                    experimentId,
                    exampleId,
                    traceId,
                    rating,
                    labels,
                    comment,
                    createdAt.toString());
        }
    }
}
