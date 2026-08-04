package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationSummary;

/** Compact evaluation experiment row for the Studio list. */
public final class EvaluationExperimentSummaryView {

    private final String experimentId;
    private final String name;
    private final String candidateId;
    private final String candidateVersion;
    private final String datasetId;
    private final String datasetVersion;
    private final String baselineExperimentId;
    private final String completedAt;
    private final EvaluationSummary summary;

    EvaluationExperimentSummaryView(EvaluationExperimentResult result) {
        this.experimentId = result.getExperimentId();
        this.name = result.getName();
        this.candidateId = result.getCandidateId();
        this.candidateVersion = result.getCandidateVersion();
        this.datasetId = result.getDatasetId();
        this.datasetVersion = result.getDatasetVersion();
        this.baselineExperimentId = result.getBaselineExperimentId();
        this.completedAt = result.getCompletedAt();
        this.summary = result.getSummary();
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getName() {
        return name;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getCandidateVersion() {
        return candidateVersion;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public String getBaselineExperimentId() {
        return baselineExperimentId;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public EvaluationSummary getSummary() {
        return summary;
    }
}
