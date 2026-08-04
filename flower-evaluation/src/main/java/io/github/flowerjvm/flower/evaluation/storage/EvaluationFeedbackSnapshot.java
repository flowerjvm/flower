package io.github.flowerjvm.flower.evaluation.storage;

import io.github.flowerjvm.flower.evaluation.EvaluationFeedback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One bounded, immutable read of locally persisted human feedback. */
public final class EvaluationFeedbackSnapshot {

    private final List<EvaluationFeedback> feedback;
    private final EvaluationLoadDiagnostics diagnostics;

    EvaluationFeedbackSnapshot(
            List<EvaluationFeedback> feedback,
            EvaluationLoadDiagnostics diagnostics) {
        this.feedback = Collections.unmodifiableList(
                new ArrayList<EvaluationFeedback>(feedback));
        this.diagnostics = diagnostics;
    }

    public List<EvaluationFeedback> getFeedback() {
        return feedback;
    }

    public EvaluationLoadDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
