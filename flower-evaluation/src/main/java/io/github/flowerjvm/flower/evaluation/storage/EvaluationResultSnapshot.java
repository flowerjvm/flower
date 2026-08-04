package io.github.flowerjvm.flower.evaluation.storage;

import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One bounded, immutable read of locally persisted experiment results. */
public final class EvaluationResultSnapshot {

    private final List<EvaluationExperimentResult> experiments;
    private final EvaluationLoadDiagnostics diagnostics;

    EvaluationResultSnapshot(
            List<EvaluationExperimentResult> experiments,
            EvaluationLoadDiagnostics diagnostics) {
        this.experiments = Collections.unmodifiableList(
                new ArrayList<EvaluationExperimentResult>(experiments));
        this.diagnostics = diagnostics;
    }

    public List<EvaluationExperimentResult> getExperiments() {
        return experiments;
    }

    public EvaluationLoadDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
