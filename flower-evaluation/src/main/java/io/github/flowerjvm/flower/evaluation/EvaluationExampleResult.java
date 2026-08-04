package io.github.flowerjvm.flower.evaluation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Persistable result for one example execution and all of its scores. */
public final class EvaluationExampleResult {

    private final String exampleId;
    private final EvaluationCaseStatus status;
    private final String traceId;
    private final String runId;
    private final String startedAt;
    private final String completedAt;
    private final Map<String, Object> input;
    private final Map<String, Object> expected;
    private final Map<String, Object> actual;
    private final Map<String, Double> metrics;
    private final List<EvaluationScore> scores;
    private final String errorType;

    @JsonCreator
    public EvaluationExampleResult(
            @JsonProperty("exampleId") String exampleId,
            @JsonProperty("status") EvaluationCaseStatus status,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("runId") String runId,
            @JsonProperty("startedAt") String startedAt,
            @JsonProperty("completedAt") String completedAt,
            @JsonProperty("input") Map<String, Object> input,
            @JsonProperty("expected") Map<String, Object> expected,
            @JsonProperty("actual") Map<String, Object> actual,
            @JsonProperty("metrics") Map<String, Double> metrics,
            @JsonProperty("scores") List<EvaluationScore> scores,
            @JsonProperty("errorType") String errorType) {
        this.exampleId = EvaluationValues.requireText("exampleId", exampleId);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.traceId = EvaluationValues.cleanText(traceId);
        this.runId = EvaluationValues.cleanText(runId);
        this.startedAt = instant("startedAt", startedAt);
        this.completedAt = instant("completedAt", completedAt);
        this.input = EvaluationValues.immutableMap(input);
        this.expected = EvaluationValues.immutableMap(expected);
        this.actual = EvaluationValues.immutableMap(actual);
        this.metrics = EvaluationValues.immutableMetrics(metrics);
        List<EvaluationScore> selected = scores == null
                ? Collections.<EvaluationScore>emptyList() : scores;
        this.scores = Collections.unmodifiableList(new ArrayList<EvaluationScore>(selected));
        this.errorType = EvaluationValues.cleanText(errorType);
    }

    public String getExampleId() {
        return exampleId;
    }

    public EvaluationCaseStatus getStatus() {
        return status;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getRunId() {
        return runId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public Map<String, Object> getExpected() {
        return expected;
    }

    public Map<String, Object> getActual() {
        return actual;
    }

    public Map<String, Double> getMetrics() {
        return metrics;
    }

    public List<EvaluationScore> getScores() {
        return scores;
    }

    public String getErrorType() {
        return errorType;
    }

    public double meanScore() {
        double sum = 0.0d;
        int count = 0;
        for (EvaluationScore score : scores) {
            if (score.getVerdict() != EvaluationVerdict.ERROR) {
                sum += score.getValue();
                count++;
            }
        }
        return count == 0 ? 0.0d : sum / count;
    }

    private static String instant(String name, String value) {
        String selected = EvaluationValues.requireText(name, value);
        Instant.parse(selected);
        return selected;
    }
}
