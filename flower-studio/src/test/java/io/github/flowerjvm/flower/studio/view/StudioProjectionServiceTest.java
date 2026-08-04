package io.github.flowerjvm.flower.studio.view;

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

class StudioProjectionServiceTest {

    @Test
    void projects_cross_runtime_run_hierarchy_timeline_metrics_and_artifacts() {
        StudioProjectionService service = new StudioProjectionService(true);

        TraceDetailView detail = service.detail(snapshot(completedTrace()), "trace-ops");

        assertThat(detail).isNotNull();
        TraceSummaryView summary = detail.getSummary();
        assertThat(summary.getDisplayName()).isEqualTo("game-server-ops / incident-42");
        assertThat(summary.getStatus()).isEqualTo(TraceStatus.COMPLETED);
        assertThat(summary.getRunCount()).isEqualTo(3);
        assertThat(summary.getModelCalls()).isEqualTo(1);
        assertThat(summary.getToolCalls()).isEqualTo(1);
        assertThat(summary.getActions()).isEqualTo(1);
        assertThat(summary.getInputTokens()).isEqualTo(120L);
        assertThat(summary.getOutputTokens()).isEqualTo(45L);
        assertThat(summary.getDurationMillis()).isEqualTo(1_100L);
        assertThat(summary.getSources()).containsExactly(
                "flower-action-runtime", "flower-agent", "flower-core");

        assertThat(detail.getRuns()).extracting(RunSummaryView::getDepth)
                .containsExactly(0, 1, 2);
        assertThat(detail.getRuns()).extracting(RunSummaryView::getStatus)
                .containsOnly(TraceStatus.COMPLETED);

        TimelineEventView modelCompleted = event(detail, "model-completed");
        assertThat(modelCompleted.getDurationMillis()).isEqualTo(150L);
        TimelineEventView toolCompleted = event(detail, "tool-completed");
        assertThat(toolCompleted.getDurationMillis()).isEqualTo(100L);
        assertThat(toolCompleted.getArtifacts()).hasSize(1);
        assertThat(toolCompleted.getArtifacts().get(0).getDownloadUrl())
                .isEqualTo("/api/artifacts?location=trace-ops%2Fevidence.json");
        assertThat(event(detail, "action-completed").getDurationMillis()).isEqualTo(300L);
    }

    @Test
    void list_filters_by_source_status_and_text() {
        StudioProjectionService service = new StudioProjectionService(false);
        List<ObservationRecord> events = new ArrayList<ObservationRecord>(completedTrace());
        events.add(event(
                "waiting-1", "flower-agent", "RUN_STARTED", "trace-wait", "agent-wait",
                null, null, "react", 1, 2_000, map("agent.id", "yard-planner")));
        events.add(event(
                "waiting-2", "flower-agent", "RUN_INTERRUPTED", "trace-wait", "agent-wait",
                null, null, "approval", 2, 2_100, map("agent.id", "yard-planner")));

        TraceListView result = service.list(
                snapshot(events),
                new StudioQuery("yard", "flower-agent", TraceStatus.INTERRUPTED, 10));

        assertThat(result.getTotalMatched()).isEqualTo(1);
        assertThat(result.getTraces()).extracting(TraceSummaryView::getTraceId)
                .containsExactly("trace-wait");
        assertThat(result.getSources()).contains(
                "flower-core", "flower-agent", "flower-action-runtime");
    }

    @Test
    void completed_root_run_remains_the_trace_outcome_when_a_child_failure_was_handled() {
        StudioProjectionService service = new StudioProjectionService(false);
        List<ObservationRecord> events = Arrays.asList(
                event(
                        "flow-start", "flower-core", "FLOW_STARTED", "trace-handled", "flow-handled",
                        null, null, null, 1, 0,
                        map("flower.flow.type", "approval-fallback")),
                event(
                        "action-denied", "flower-action-runtime", "ACTION_DENIED", "trace-handled", "action-denied",
                        "flow-handled", "proposal-1", "yard.plan.publish", 1, 100,
                        map("action.id", "yard.plan.publish")),
                event(
                        "flow-complete", "flower-core", "FLOW_COMPLETED", "trace-handled", "flow-handled",
                        null, null, null, 2, 200,
                        map("flower.flow.type", "approval-fallback")));

        TraceDetailView detail = service.detail(snapshot(events), "trace-handled");

        assertThat(detail.getSummary().getStatus()).isEqualTo(TraceStatus.COMPLETED);
        assertThat(detail.getSummary().getFailures()).isEqualTo(1);
        assertThat(detail.getRuns()).extracting(RunSummaryView::getStatus)
                .containsExactly(TraceStatus.COMPLETED, TraceStatus.FAILED);
    }

    private static List<ObservationRecord> completedTrace() {
        List<ObservationRecord> events = new ArrayList<ObservationRecord>();
        events.add(event(
                "flow-started", "flower-core", "FLOW_STARTED", "trace-ops", "flow-run",
                null, null, null, 1, 0,
                map("flower.flow.type", "game-server-ops", "flower.flow.key", "incident-42")));
        events.add(event(
                "agent-started", "flower-agent", "RUN_STARTED", "trace-ops", "agent-run",
                "flow-run", null, "react", 1, 100,
                map("agent.id", "game-ops-agent", "agent.recipe.id", "react")));
        events.add(event(
                "model-submitted", "flower-agent", "MODEL_CALL_SUBMITTED", "trace-ops", "agent-run",
                "flow-run", "model-1", "gpt-compatible", 2, 150,
                Collections.<String, Object>emptyMap()));
        events.add(event(
                "model-completed", "flower-agent", "MODEL_CALL_COMPLETED", "trace-ops", "agent-run",
                "flow-run", "model-1", "gpt-compatible", 3, 300,
                map("ai.usage.input.tokens", 120, "ai.usage.output.tokens", 45)));
        events.add(event(
                "tool-started", "flower-agent", "TOOL_CALL_STARTED", "trace-ops", "agent-run",
                "flow-run", "tool-1", "game.server.logs.search", 4, 400,
                Collections.<String, Object>emptyMap()));
        Map<String, Object> artifact = map(
                "capture", "ARTIFACT",
                "artifactId", "evidence",
                "location", "trace-ops/evidence.json",
                "mediaType", "application/json",
                "sizeBytes", 42);
        events.add(event(
                "tool-completed", "flower-agent", "TOOL_CALL_COMPLETED", "trace-ops", "agent-run",
                "flow-run", "tool-1", "game.server.logs.search", 5, 500,
                map("agent.tool.result", artifact)));
        events.add(event(
                "action-proposed", "flower-action-runtime", "ACTION_PROPOSED", "trace-ops", "action-run",
                "agent-run", "action-1", "game.server.restart", 1, 550,
                map("action.id", "game.server.restart")));
        events.add(event(
                "action-started", "flower-action-runtime", "ACTION_EXECUTION_STARTED", "trace-ops", "action-run",
                "agent-run", "action-1", "game.server.restart", 2, 600,
                map("action.id", "game.server.restart")));
        events.add(event(
                "action-completed", "flower-action-runtime", "ACTION_EXECUTION_COMPLETED", "trace-ops", "action-run",
                "agent-run", "action-1", "game.server.restart", 3, 900,
                map("action.id", "game.server.restart", "status", "SUCCEEDED")));
        events.add(event(
                "agent-completed", "flower-agent", "RUN_COMPLETED", "trace-ops", "agent-run",
                "flow-run", null, "react", 6, 1_000,
                map("agent.id", "game-ops-agent", "agent.recipe.id", "react")));
        events.add(event(
                "flow-completed", "flower-core", "FLOW_COMPLETED", "trace-ops", "flow-run",
                null, null, null, 2, 1_100,
                map("flower.flow.type", "game-server-ops", "flower.flow.key", "incident-42")));
        return events;
    }

    private static TimelineEventView event(TraceDetailView detail, String eventId) {
        for (TimelineEventView event : detail.getEvents()) {
            if (eventId.equals(event.getEventId())) {
                return event;
            }
        }
        throw new AssertionError("missing timeline event " + eventId);
    }

    private static ObservationRecord event(
            String eventId,
            String source,
            String type,
            String traceId,
            String runId,
            String parentRunId,
            String operationId,
            String operationName,
            long sequence,
            long millis,
            Map<String, Object> attributes) {
        return new ObservationRecord(
                1,
                source,
                eventId,
                type,
                traceId,
                runId,
                parentRunId,
                operationId,
                operationName,
                sequence,
                Instant.parse("2026-08-01T00:00:00Z").plusMillis(millis),
                attributes);
    }

    private static StudioSnapshot snapshot(List<ObservationRecord> events) {
        StudioDiagnostics diagnostics = new StudioDiagnostics(
                "observations.jsonl",
                true,
                1L,
                events.size(),
                events.size(),
                0L,
                0L,
                0L,
                0L,
                Collections.<Long>emptyList(),
                Instant.parse("2026-08-01T00:01:00Z"));
        return new StudioSnapshot(events, diagnostics);
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> selected = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            selected.put(String.valueOf(values[index]), values[index + 1]);
        }
        return selected;
    }
}
