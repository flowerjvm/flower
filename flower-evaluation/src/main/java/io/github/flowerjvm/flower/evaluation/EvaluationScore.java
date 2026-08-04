package io.github.flowerjvm.flower.evaluation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Normalized evaluator score with a 0..1 value and explicit threshold. */
public final class EvaluationScore {

    private final String evaluatorId;
    private final double value;
    private final double threshold;
    private final EvaluationVerdict verdict;
    private final boolean required;
    private final String reason;

    @JsonCreator
    public EvaluationScore(
            @JsonProperty("evaluatorId") String evaluatorId,
            @JsonProperty("value") double value,
            @JsonProperty("threshold") double threshold,
            @JsonProperty("verdict") EvaluationVerdict verdict,
            @JsonProperty("required") boolean required,
            @JsonProperty("reason") String reason) {
        this.evaluatorId = EvaluationValues.requireText("evaluatorId", evaluatorId);
        this.value = score("value", value);
        this.threshold = score("threshold", threshold);
        if (verdict == null) {
            throw new IllegalArgumentException("verdict must not be null");
        }
        this.verdict = verdict;
        this.required = required;
        this.reason = EvaluationValues.cleanText(reason);
    }

    public static EvaluationScore scored(
            String evaluatorId,
            double value,
            double threshold,
            String reason) {
        return new EvaluationScore(
                evaluatorId,
                value,
                threshold,
                value >= threshold ? EvaluationVerdict.PASS : EvaluationVerdict.FAIL,
                true,
                reason);
    }

    public static EvaluationScore error(String evaluatorId, String errorType) {
        return new EvaluationScore(
                evaluatorId,
                0.0d,
                1.0d,
                EvaluationVerdict.ERROR,
                true,
                EvaluationValues.cleanText(errorType));
    }

    EvaluationScore withRequired(boolean selected) {
        return new EvaluationScore(
                evaluatorId,
                value,
                threshold,
                verdict,
                selected,
                reason);
    }

    public String getEvaluatorId() {
        return evaluatorId;
    }

    public double getValue() {
        return value;
    }

    public double getThreshold() {
        return threshold;
    }

    public EvaluationVerdict getVerdict() {
        return verdict;
    }

    public boolean isRequired() {
        return required;
    }

    public String getReason() {
        return reason;
    }

    private static double score(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }
}
