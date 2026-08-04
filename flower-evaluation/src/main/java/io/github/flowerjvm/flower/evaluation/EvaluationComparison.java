package io.github.flowerjvm.flower.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Regression-oriented comparison between a baseline and candidate experiment. */
public final class EvaluationComparison {

    private final String baselineExperimentId;
    private final String candidateExperimentId;
    private final double passRateDelta;
    private final double meanScoreDelta;
    private final List<String> regressedExampleIds;
    private final List<String> improvedExampleIds;
    private final List<EvaluationMetricDelta> evaluatorDeltas;

    EvaluationComparison(
            String baselineExperimentId,
            String candidateExperimentId,
            double passRateDelta,
            double meanScoreDelta,
            List<String> regressedExampleIds,
            List<String> improvedExampleIds,
            List<EvaluationMetricDelta> evaluatorDeltas) {
        this.baselineExperimentId = EvaluationValues.requireText(
                "baselineExperimentId", baselineExperimentId);
        this.candidateExperimentId = EvaluationValues.requireText(
                "candidateExperimentId", candidateExperimentId);
        this.passRateDelta = passRateDelta;
        this.meanScoreDelta = meanScoreDelta;
        this.regressedExampleIds = immutable(regressedExampleIds);
        this.improvedExampleIds = immutable(improvedExampleIds);
        this.evaluatorDeltas = Collections.unmodifiableList(
                new ArrayList<EvaluationMetricDelta>(evaluatorDeltas));
    }

    public String getBaselineExperimentId() {
        return baselineExperimentId;
    }

    public String getCandidateExperimentId() {
        return candidateExperimentId;
    }

    public double getPassRateDelta() {
        return passRateDelta;
    }

    public double getMeanScoreDelta() {
        return meanScoreDelta;
    }

    public List<String> getRegressedExampleIds() {
        return regressedExampleIds;
    }

    public List<String> getImprovedExampleIds() {
        return improvedExampleIds;
    }

    public List<EvaluationMetricDelta> getEvaluatorDeltas() {
        return evaluatorDeltas;
    }

    public boolean hasRegression() {
        return !regressedExampleIds.isEmpty()
                || passRateDelta < 0.0d
                || meanScoreDelta < 0.0d;
    }

    private static List<String> immutable(List<String> source) {
        List<String> copy = new ArrayList<String>();
        for (String value : source) {
            copy.add(EvaluationValues.requireText("example id", value));
        }
        return Collections.unmodifiableList(copy);
    }
}
