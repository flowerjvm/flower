package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.evaluation.EvaluationCaseStatus;
import io.github.flowerjvm.flower.evaluation.EvaluationExampleResult;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationScore;
import io.github.flowerjvm.flower.evaluation.EvaluationSummary;
import io.github.flowerjvm.flower.studio.model.ObservationRecord;
import io.github.flowerjvm.flower.studio.store.StudioDiagnostics;
import io.github.flowerjvm.flower.studio.store.StudioSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringProjectionServiceTest {

    @Test
    void projects_runtime_quality_operation_and_transition_health() {
        StudioSnapshot snapshot = new StudioSnapshot(
                observations(),
                new StudioDiagnostics(
                        "observations.jsonl",
                        true,
                        1_024L,
                        14L,
                        14L,
                        0L,
                        0L,
                        0L,
                        0L,
                        Collections.<Long>emptyList(),
                        Instant.parse("2026-08-05T00:02:00Z")));
        MonitoringDashboardView dashboard = new MonitoringProjectionService().project(
                snapshot,
                Arrays.asList(
                        evaluation("baseline", null, false, "2026-08-05T00:00:00Z"),
                        evaluation("candidate", "baseline", true, "2026-08-05T00:01:00Z")),
                null);

        MonitoringOverviewView overview = dashboard.getOverview();
        assertThat(overview.getTraceCount()).isEqualTo(2);
        assertThat(overview.getCompleted()).isEqualTo(1);
        assertThat(overview.getFailed()).isEqualTo(1);
        assertThat(overview.getAverageTraceDurationMillis()).isEqualTo(7_000L);
        assertThat(overview.getModelCalls()).isEqualTo(1);
        assertThat(overview.getToolCalls()).isEqualTo(1);
        assertThat(overview.getToolFailures()).isEqualTo(1);
        assertThat(overview.getApprovalsRequested()).isEqualTo(1);
        assertThat(overview.getTimeouts()).isEqualTo(1);
        assertThat(overview.getInputTokens()).isEqualTo(120L);
        assertThat(overview.getOutputTokens()).isEqualTo(30L);

        assertThat(status(dashboard, TraceStatus.COMPLETED).getRatio()).isEqualTo(0.5d);
        MonitoringOperationView tool = operation(dashboard, "TOOL", "logs.search");
        assertThat(tool.getCount()).isEqualTo(1);
        assertThat(tool.getFailures()).isEqualTo(1);
        assertThat(tool.getFailureRate()).isEqualTo(1.0d);
        assertThat(tool.getAverageDurationMillis()).isEqualTo(1_000L);

        assertThat(dashboard.getTransitions()).hasSize(2);
        MonitoringTransitionView transition = transition(dashboard, "investigate");
        assertThat(transition.getOutcome()).isEqualTo("NEXT");
        assertThat(transition.getTargetStepId()).isEqualTo("verify");
        assertThat(dashboard.getActivity()).hasSize(2);
        assertThat(dashboard.getSources()).extracting(MonitoringSourceView::getSource)
                .containsExactly("flower-core");

        MonitoringEvaluationView quality = dashboard.getEvaluation();
        assertThat(quality.getExperimentCount()).isEqualTo(2);
        assertThat(quality.getPassRate()).isEqualTo(0.5d);
        assertThat(quality.getLatestExperimentId()).isEqualTo("candidate");
        assertThat(quality.getLatestPassRate()).isEqualTo(1.0d);
        assertThat(quality.getImprovedExamples()).isEqualTo(1);
        assertThat(quality.getRegressedExamples()).isZero();
    }

    @Test
    void returns_stable_empty_aggregates_without_observations_or_evaluations() {
        StudioSnapshot snapshot = new StudioSnapshot(
                Collections.<ObservationRecord>emptyList(),
                new StudioDiagnostics(
                        "empty.jsonl",
                        true,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        Collections.<Long>emptyList(),
                        Instant.parse("2026-08-05T00:00:00Z")));

        MonitoringDashboardView dashboard = new MonitoringProjectionService().project(
                snapshot,
                Collections.<EvaluationExperimentResult>emptyList(),
                null);

        assertThat(dashboard.getOverview().getWindowStart()).isNull();
        assertThat(dashboard.getOverview().getTraceCount()).isZero();
        assertThat(dashboard.getStatuses()).hasSize(TraceStatus.values().length);
        assertThat(dashboard.getActivity()).isEmpty();
        assertThat(dashboard.getOperations()).isEmpty();
        assertThat(dashboard.getEvaluation().getExperimentCount()).isZero();
    }

    private static List<ObservationRecord> observations() {
        List<ObservationRecord> events = new ArrayList<ObservationRecord>();
        events.add(event(1, "FLOW_STARTED", "trace-ok", "run-ok", null, "flow",
                "2026-08-05T00:00:00Z", attributes("flower.flow.type", "ops")));
        events.add(event(2, "STEP_STARTED", "trace-ok", "run-ok", "step-1", null,
                "2026-08-05T00:00:01Z", attributes("flower.step.id", "investigate")));
        events.add(event(3, "MODEL_CALL_SUBMITTED", "trace-ok", "run-ok", "model-1", "gpt",
                "2026-08-05T00:00:02Z", null));
        events.add(event(4, "MODEL_CALL_COMPLETED", "trace-ok", "run-ok", "model-1", "gpt",
                "2026-08-05T00:00:03Z", attributes(
                        "ai.usage.input.tokens", 120L,
                        "ai.usage.output.tokens", 30L)));
        events.add(event(5, "TOOL_CALL_STARTED", "trace-ok", "run-ok", "tool-1", "logs.search",
                "2026-08-05T00:00:04Z", null));
        events.add(event(6, "TOOL_CALL_FAILED", "trace-ok", "run-ok", "tool-1", "logs.search",
                "2026-08-05T00:00:05Z", null));
        events.add(event(7, "STEP_COMPLETED", "trace-ok", "run-ok", "step-1", null,
                "2026-08-05T00:00:08Z", attributes(
                        "flower.step.id", "investigate",
                        "flower.step.outcome", "NEXT",
                        "flower.step.target.id", "verify")));
        events.add(event(8, "FLOW_COMPLETED", "trace-ok", "run-ok", null, "flow",
                "2026-08-05T00:00:09Z", null));

        events.add(event(9, "FLOW_STARTED", "trace-failed", "run-failed", null, "flow",
                "2026-08-05T00:01:00Z", attributes("flower.flow.type", "ops")));
        events.add(event(10, "FLOW_WAITING", "trace-failed", "run-failed", null, "flow",
                "2026-08-05T00:01:01Z", null));
        events.add(event(11, "APPROVAL_REQUESTED", "trace-failed", "run-failed", null, "approval",
                "2026-08-05T00:01:02Z", null));
        events.add(event(12, "FLOW_RESUMED", "trace-failed", "run-failed", null, "flow",
                "2026-08-05T00:01:03Z", attributes("flower.resume.reason", "TIMEOUT")));
        events.add(event(13, "STEP_FAILED", "trace-failed", "run-failed", "step-2", null,
                "2026-08-05T00:01:04Z", attributes("flower.step.id", "approval")));
        events.add(event(14, "FLOW_FAILED", "trace-failed", "run-failed", null, "flow",
                "2026-08-05T00:01:05Z", null));
        return events;
    }

    private static ObservationRecord event(
            long sequence,
            String type,
            String traceId,
            String runId,
            String operationId,
            String operationName,
            String occurredAt,
            Map<String, Object> attributes) {
        return new ObservationRecord(
                1,
                "flower-core",
                "event-" + sequence,
                type,
                traceId,
                runId,
                null,
                operationId,
                operationName,
                sequence,
                Instant.parse(occurredAt),
                attributes);
    }

    private static EvaluationExperimentResult evaluation(
            String id,
            String baselineId,
            boolean passed,
            String completedAt) {
        EvaluationCaseStatus status = passed
                ? EvaluationCaseStatus.PASS : EvaluationCaseStatus.FAIL;
        EvaluationExampleResult selected = new EvaluationExampleResult(
                "case-1",
                status,
                "trace-ok",
                "run-ok",
                "2026-08-05T00:00:00Z",
                completedAt,
                Collections.<String, Object>emptyMap(),
                Collections.<String, Object>emptyMap(),
                Collections.<String, Object>emptyMap(),
                Collections.<String, Double>emptyMap(),
                Collections.singletonList(EvaluationScore.scored(
                        "expected", passed ? 1.0d : 0.0d, 1.0d, null)),
                null);
        EvaluationSummary summary = new EvaluationSummary(
                1,
                passed ? 1 : 0,
                passed ? 0 : 1,
                0,
                passed ? 1.0d : 0.0d,
                passed ? 1.0d : 0.0d,
                100L,
                10L,
                5L,
                1L);
        return new EvaluationExperimentResult(
                1,
                id,
                id,
                baselineId,
                "dataset",
                "dataset",
                "v1",
                "agent",
                id,
                Collections.<String, Object>emptyMap(),
                Collections.<String, Object>emptyMap(),
                "2026-08-05T00:00:00Z",
                completedAt,
                summary,
                Collections.singletonList(selected));
    }

    private static Map<String, Object> attributes(Object... values) {
        if (values == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            attributes.put((String) values[index], values[index + 1]);
        }
        return attributes;
    }

    private static MonitoringStatusView status(
            MonitoringDashboardView dashboard,
            TraceStatus status) {
        for (MonitoringStatusView selected : dashboard.getStatuses()) {
            if (selected.getStatus() == status) {
                return selected;
            }
        }
        throw new AssertionError("Missing status " + status);
    }

    private static MonitoringOperationView operation(
            MonitoringDashboardView dashboard,
            String category,
            String name) {
        for (MonitoringOperationView selected : dashboard.getOperations()) {
            if (category.equals(selected.getCategory()) && name.equals(selected.getName())) {
                return selected;
            }
        }
        throw new AssertionError("Missing operation " + category + " / " + name);
    }

    private static MonitoringTransitionView transition(
            MonitoringDashboardView dashboard,
            String stepId) {
        for (MonitoringTransitionView selected : dashboard.getTransitions()) {
            if (stepId.equals(selected.getStepId())) {
                return selected;
            }
        }
        throw new AssertionError("Missing transition " + stepId);
    }
}
