package io.github.flowerjvm.flower.evaluation;

/** Mean-score change for one evaluator between two experiments. */
public final class EvaluationMetricDelta {

    private final String evaluatorId;
    private final double baselineMean;
    private final double candidateMean;
    private final double delta;

    public EvaluationMetricDelta(
            String evaluatorId,
            double baselineMean,
            double candidateMean) {
        this.evaluatorId = EvaluationValues.requireText("evaluatorId", evaluatorId);
        this.baselineMean = score("baselineMean", baselineMean);
        this.candidateMean = score("candidateMean", candidateMean);
        this.delta = candidateMean - baselineMean;
    }

    public String getEvaluatorId() {
        return evaluatorId;
    }

    public double getBaselineMean() {
        return baselineMean;
    }

    public double getCandidateMean() {
        return candidateMean;
    }

    public double getDelta() {
        return delta;
    }

    private static double score(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }
}
