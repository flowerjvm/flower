package io.github.flowerjvm.flower.evaluation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Aggregate counts, score, usage, and duration for one experiment. */
public final class EvaluationSummary {

    private final int total;
    private final int passed;
    private final int failed;
    private final int errors;
    private final double passRate;
    private final double meanScore;
    private final long durationMillis;
    private final long inputTokens;
    private final long outputTokens;
    private final long toolCalls;

    @JsonCreator
    public EvaluationSummary(
            @JsonProperty("total") int total,
            @JsonProperty("passed") int passed,
            @JsonProperty("failed") int failed,
            @JsonProperty("errors") int errors,
            @JsonProperty("passRate") double passRate,
            @JsonProperty("meanScore") double meanScore,
            @JsonProperty("durationMillis") long durationMillis,
            @JsonProperty("inputTokens") long inputTokens,
            @JsonProperty("outputTokens") long outputTokens,
            @JsonProperty("toolCalls") long toolCalls) {
        if (total < 0 || passed < 0 || failed < 0 || errors < 0
                || passed + failed + errors != total) {
            throw new IllegalArgumentException("invalid evaluation summary counts");
        }
        this.total = total;
        this.passed = passed;
        this.failed = failed;
        this.errors = errors;
        this.passRate = ratio("passRate", passRate);
        this.meanScore = ratio("meanScore", meanScore);
        if (durationMillis < 0L || inputTokens < 0L || outputTokens < 0L || toolCalls < 0L) {
            throw new IllegalArgumentException("summary usage values must not be negative");
        }
        this.durationMillis = durationMillis;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.toolCalls = toolCalls;
    }

    public int getTotal() {
        return total;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }

    public int getErrors() {
        return errors;
    }

    public double getPassRate() {
        return passRate;
    }

    public double getMeanScore() {
        return meanScore;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getToolCalls() {
        return toolCalls;
    }

    private static double ratio(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }
}
