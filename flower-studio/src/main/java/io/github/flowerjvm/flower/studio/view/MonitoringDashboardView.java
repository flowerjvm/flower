package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.evaluation.storage.EvaluationLoadDiagnostics;
import io.github.flowerjvm.flower.studio.store.StudioDiagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete read-only monitoring projection for the local Studio dashboard. */
public final class MonitoringDashboardView {

    private final MonitoringOverviewView overview;
    private final List<MonitoringStatusView> statuses;
    private final List<MonitoringTimeBucketView> activity;
    private final List<MonitoringOperationView> operations;
    private final List<MonitoringTransitionView> transitions;
    private final List<MonitoringSourceView> sources;
    private final MonitoringEvaluationView evaluation;
    private final StudioDiagnostics traceDiagnostics;
    private final EvaluationLoadDiagnostics evaluationDiagnostics;

    MonitoringDashboardView(
            MonitoringOverviewView overview,
            List<MonitoringStatusView> statuses,
            List<MonitoringTimeBucketView> activity,
            List<MonitoringOperationView> operations,
            List<MonitoringTransitionView> transitions,
            List<MonitoringSourceView> sources,
            MonitoringEvaluationView evaluation,
            StudioDiagnostics traceDiagnostics,
            EvaluationLoadDiagnostics evaluationDiagnostics) {
        this.overview = overview;
        this.statuses = immutable(statuses);
        this.activity = immutable(activity);
        this.operations = immutable(operations);
        this.transitions = immutable(transitions);
        this.sources = immutable(sources);
        this.evaluation = evaluation;
        this.traceDiagnostics = traceDiagnostics;
        this.evaluationDiagnostics = evaluationDiagnostics;
    }

    public MonitoringOverviewView getOverview() { return overview; }
    public List<MonitoringStatusView> getStatuses() { return statuses; }
    public List<MonitoringTimeBucketView> getActivity() { return activity; }
    public List<MonitoringOperationView> getOperations() { return operations; }
    public List<MonitoringTransitionView> getTransitions() { return transitions; }
    public List<MonitoringSourceView> getSources() { return sources; }
    public MonitoringEvaluationView getEvaluation() { return evaluation; }
    public StudioDiagnostics getTraceDiagnostics() { return traceDiagnostics; }
    public EvaluationLoadDiagnostics getEvaluationDiagnostics() { return evaluationDiagnostics; }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
