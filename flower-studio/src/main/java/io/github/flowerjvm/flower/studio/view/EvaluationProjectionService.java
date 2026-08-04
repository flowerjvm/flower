package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.evaluation.EvaluationComparator;
import io.github.flowerjvm.flower.evaluation.EvaluationComparison;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationFeedback;
import io.github.flowerjvm.flower.evaluation.storage.EvaluationFeedbackSnapshot;
import io.github.flowerjvm.flower.evaluation.storage.EvaluationResultSnapshot;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationFeedbackSource;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationResultSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds read-only Studio projections from evaluation JSON Lines sources. */
public final class EvaluationProjectionService {

    private final JsonLinesEvaluationResultSource resultSource;
    private final JsonLinesEvaluationFeedbackSource feedbackSource;

    public EvaluationProjectionService(
            JsonLinesEvaluationResultSource resultSource,
            JsonLinesEvaluationFeedbackSource feedbackSource) {
        if (resultSource == null || feedbackSource == null) {
            throw new IllegalArgumentException("evaluation sources must not be null");
        }
        this.resultSource = resultSource;
        this.feedbackSource = feedbackSource;
    }

    public EvaluationListView list() throws IOException {
        EvaluationResultSnapshot results = resultSource.load();
        EvaluationFeedbackSnapshot feedback = feedbackSource.load();
        List<EvaluationExperimentSummaryView> summaries =
                new ArrayList<EvaluationExperimentSummaryView>();
        for (EvaluationExperimentResult result : results.getExperiments()) {
            summaries.add(new EvaluationExperimentSummaryView(result));
        }
        return new EvaluationListView(
                summaries,
                results.getDiagnostics(),
                feedback.getDiagnostics());
    }

    public EvaluationExperimentDetailView detail(String experimentId) throws IOException {
        if (experimentId == null || experimentId.trim().isEmpty()) {
            return null;
        }
        EvaluationResultSnapshot results = resultSource.load();
        Map<String, EvaluationExperimentResult> byId =
                new LinkedHashMap<String, EvaluationExperimentResult>();
        for (EvaluationExperimentResult result : results.getExperiments()) {
            byId.put(result.getExperimentId(), result);
        }
        EvaluationExperimentResult selected = byId.get(experimentId);
        if (selected == null) {
            return null;
        }

        EvaluationComparison comparison = null;
        String comparisonStatus = "NOT_CONFIGURED";
        String baselineId = selected.getBaselineExperimentId();
        if (baselineId != null) {
            EvaluationExperimentResult baseline = byId.get(baselineId);
            if (baseline == null) {
                comparisonStatus = "BASELINE_NOT_FOUND";
            } else {
                try {
                    comparison = EvaluationComparator.compare(baseline, selected);
                    comparisonStatus = "AVAILABLE";
                } catch (IllegalArgumentException incompatible) {
                    comparisonStatus = "INCOMPATIBLE_BASELINE";
                }
            }
        }

        List<EvaluationFeedback> selectedFeedback = new ArrayList<EvaluationFeedback>();
        for (EvaluationFeedback feedback : feedbackSource.load().getFeedback()) {
            if (selected.getExperimentId().equals(feedback.getExperimentId())) {
                selectedFeedback.add(feedback);
            }
        }
        return new EvaluationExperimentDetailView(
                selected,
                comparison,
                comparisonStatus,
                selectedFeedback);
    }
}
