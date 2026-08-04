package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.evaluation.EvaluationComparison;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationFeedback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detailed Studio projection for one experiment and its optional baseline. */
public final class EvaluationExperimentDetailView {

    private final EvaluationExperimentResult experiment;
    private final EvaluationComparison comparison;
    private final String comparisonStatus;
    private final List<EvaluationFeedback> feedback;

    EvaluationExperimentDetailView(
            EvaluationExperimentResult experiment,
            EvaluationComparison comparison,
            String comparisonStatus,
            List<EvaluationFeedback> feedback) {
        this.experiment = experiment;
        this.comparison = comparison;
        this.comparisonStatus = comparisonStatus;
        this.feedback = Collections.unmodifiableList(new ArrayList<EvaluationFeedback>(feedback));
    }

    public EvaluationExperimentResult getExperiment() {
        return experiment;
    }

    public EvaluationComparison getComparison() {
        return comparison;
    }

    public String getComparisonStatus() {
        return comparisonStatus;
    }

    public List<EvaluationFeedback> getFeedback() {
        return feedback;
    }
}
