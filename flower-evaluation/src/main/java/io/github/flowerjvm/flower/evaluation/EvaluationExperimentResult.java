package io.github.flowerjvm.flower.evaluation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Immutable, persistable output of one evaluation experiment. */
public final class EvaluationExperimentResult {

    public static final int SCHEMA_VERSION = 1;
    public static final String RECORD_TYPE = "EXPERIMENT_RESULT";

    private final int schemaVersion;
    private final String experimentId;
    private final String name;
    private final String baselineExperimentId;
    private final String datasetId;
    private final String datasetName;
    private final String datasetVersion;
    private final String candidateId;
    private final String candidateVersion;
    private final Map<String, Object> candidateAttributes;
    private final Map<String, Object> metadata;
    private final String startedAt;
    private final String completedAt;
    private final EvaluationSummary summary;
    private final List<EvaluationExampleResult> cases;

    @JsonCreator
    public EvaluationExperimentResult(
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("experimentId") String experimentId,
            @JsonProperty("name") String name,
            @JsonProperty("baselineExperimentId") String baselineExperimentId,
            @JsonProperty("datasetId") String datasetId,
            @JsonProperty("datasetName") String datasetName,
            @JsonProperty("datasetVersion") String datasetVersion,
            @JsonProperty("candidateId") String candidateId,
            @JsonProperty("candidateVersion") String candidateVersion,
            @JsonProperty("candidateAttributes") Map<String, Object> candidateAttributes,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("startedAt") String startedAt,
            @JsonProperty("completedAt") String completedAt,
            @JsonProperty("summary") EvaluationSummary summary,
            @JsonProperty("cases") List<EvaluationExampleResult> cases) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.experimentId = EvaluationValues.requireText("experimentId", experimentId);
        this.name = EvaluationValues.requireText("name", name);
        this.baselineExperimentId = EvaluationValues.cleanText(baselineExperimentId);
        this.datasetId = EvaluationValues.requireText("datasetId", datasetId);
        this.datasetName = EvaluationValues.requireText("datasetName", datasetName);
        this.datasetVersion = EvaluationValues.requireText("datasetVersion", datasetVersion);
        this.candidateId = EvaluationValues.requireText("candidateId", candidateId);
        this.candidateVersion = EvaluationValues.requireText("candidateVersion", candidateVersion);
        this.candidateAttributes = EvaluationValues.immutableMap(candidateAttributes);
        this.metadata = EvaluationValues.immutableMap(metadata);
        this.startedAt = instant("startedAt", startedAt);
        this.completedAt = instant("completedAt", completedAt);
        if (summary == null || cases == null) {
            throw new IllegalArgumentException("summary and cases must not be null");
        }
        this.summary = summary;
        this.cases = Collections.unmodifiableList(
                new ArrayList<EvaluationExampleResult>(cases));
        if (summary.getTotal() != cases.size()) {
            throw new IllegalArgumentException("summary total must match case count");
        }
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getRecordType() {
        return RECORD_TYPE;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getName() {
        return name;
    }

    public String getBaselineExperimentId() {
        return baselineExperimentId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getCandidateVersion() {
        return candidateVersion;
    }

    public Map<String, Object> getCandidateAttributes() {
        return candidateAttributes;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public EvaluationSummary getSummary() {
        return summary;
    }

    public List<EvaluationExampleResult> getCases() {
        return cases;
    }

    private static String instant(String name, String value) {
        String selected = EvaluationValues.requireText(name, value);
        Instant.parse(selected);
        return selected;
    }
}
