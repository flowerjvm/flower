package io.github.flowerjvm.flower.studio.store;

import io.github.flowerjvm.flower.studio.model.ObservationRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLinesObservationRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reads_common_and_legacy_core_events_without_exposing_malformed_content() throws Exception {
        Path traceFile = temporaryDirectory.resolve("observations.jsonl");
        Files.write(traceFile, Arrays.asList(
                commonEvent("agent-1", "RUN_STARTED", "agent-run", 1),
                "{\"apiKey\":\"must-not-leak\"",
                "",
                legacyCoreEvent(),
                commonEvent("agent-1", "RUN_STARTED", "agent-run", 1)),
                StandardCharsets.UTF_8);

        StudioSnapshot snapshot = new JsonLinesObservationRepository(traceFile).load();

        assertThat(snapshot.events()).hasSize(2);
        assertThat(snapshot.events()).extracting(ObservationRecord::source)
                .containsExactly("flower-agent", "flower-core");
        ObservationRecord legacy = snapshot.events().get(1);
        assertThat(legacy.runId()).isEqualTo("core-run");
        assertThat(legacy.attributes())
                .containsEntry("flower.flow.type", "maintenance")
                .containsEntry("flower.flow.key", "job-7")
                .containsEntry("flower.worker.name", "control");

        assertThat(snapshot.diagnostics().getMalformedLineCount()).isEqualTo(1L);
        assertThat(snapshot.diagnostics().getMalformedLineNumbers()).containsExactly(2L);
        assertThat(snapshot.diagnostics().getIgnoredLineCount()).isEqualTo(1L);
        assertThat(snapshot.diagnostics().getDuplicateEventCount()).isEqualTo(1L);
        assertThat(snapshot.diagnostics().toString()).doesNotContain("must-not-leak");
    }

    @Test
    void retains_only_the_newest_configured_number_of_events() throws Exception {
        Path traceFile = temporaryDirectory.resolve("bounded.jsonl");
        Files.write(traceFile, Arrays.asList(
                commonEvent("event-1", "RUN_STARTED", "run-1", 1),
                commonEvent("event-2", "RUN_STARTED", "run-2", 2),
                commonEvent("event-3", "RUN_COMPLETED", "run-2", 3)),
                StandardCharsets.UTF_8);

        StudioSnapshot snapshot = new JsonLinesObservationRepository(traceFile, 2).load();

        assertThat(snapshot.events()).extracting(ObservationRecord::eventId)
                .containsExactly("event-2", "event-3");
        assertThat(snapshot.diagnostics().getTruncatedEventCount()).isEqualTo(1L);
    }

    @Test
    void missing_file_is_reported_as_a_degraded_empty_snapshot_without_being_created() throws Exception {
        Path traceFile = temporaryDirectory.resolve("missing.jsonl");

        StudioSnapshot snapshot = new JsonLinesObservationRepository(traceFile).load();

        assertThat(snapshot.events()).isEmpty();
        assertThat(snapshot.diagnostics().isTraceFileExists()).isFalse();
        assertThat(traceFile).doesNotExist();
    }

    private static String commonEvent(
            String eventId,
            String eventType,
            String runId,
            long sequence) {
        return "{\"schemaVersion\":1,\"source\":\"flower-agent\","
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"traceId\":\"trace-1\",\"runId\":\"" + runId + "\","
                + "\"sequence\":" + sequence + ","
                + "\"occurredAt\":\"2026-08-01T00:00:0" + sequence + "Z\","
                + "\"attributes\":{\"agent.id\":\"ops-agent\"}}";
    }

    private static String legacyCoreEvent() {
        return "{\"schemaVersion\":1,\"source\":\"flower-core\","
                + "\"eventId\":\"core-1\",\"eventType\":\"FLOW_STARTED\","
                + "\"traceId\":\"trace-1\",\"flowRunId\":\"core-run\","
                + "\"flowType\":\"maintenance\",\"flowKey\":\"job-7\","
                + "\"workerName\":\"control\",\"sequence\":1,"
                + "\"occurredAt\":\"2026-08-01T00:00:02Z\",\"attributes\":{}}";
    }
}
