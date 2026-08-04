package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.evaluation.storage.EvaluationLoadDiagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Studio response for the evaluation experiment list. */
public final class EvaluationListView {

    private final List<EvaluationExperimentSummaryView> experiments;
    private final EvaluationLoadDiagnostics resultDiagnostics;
    private final EvaluationLoadDiagnostics feedbackDiagnostics;

    EvaluationListView(
            List<EvaluationExperimentSummaryView> experiments,
            EvaluationLoadDiagnostics resultDiagnostics,
            EvaluationLoadDiagnostics feedbackDiagnostics) {
        this.experiments = Collections.unmodifiableList(
                new ArrayList<EvaluationExperimentSummaryView>(experiments));
        this.resultDiagnostics = resultDiagnostics;
        this.feedbackDiagnostics = feedbackDiagnostics;
    }

    public List<EvaluationExperimentSummaryView> getExperiments() {
        return experiments;
    }

    public EvaluationLoadDiagnostics getResultDiagnostics() {
        return resultDiagnostics;
    }

    public EvaluationLoadDiagnostics getFeedbackDiagnostics() {
        return feedbackDiagnostics;
    }
}
