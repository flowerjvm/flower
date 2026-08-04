package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonLinesFlowerTraceSinkTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writes_one_complete_json_document_per_line() throws Exception {
        Path file = temporaryDirectory.resolve("trace/events.jsonl");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("attempt", 2);
        detail.put("flags", Arrays.asList("a", true));
        FlowerTraceEvent event = event(1,
                "message", "line one\n\"line two\"",
                "detail", detail);

        try (JsonLinesFlowerTraceSink sink = new JsonLinesFlowerTraceSink(file)) {
            sink.publish(event);
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
                .startsWith("{\"schemaVersion\":1,\"source\":\"flower-core\"")
                .contains("\"eventType\":\"FLOW_STARTED\"")
                .contains("\"flowType\":\"test\"")
                .contains("\"occurredAt\":\"1970-01-01T00:00:00.001Z\"")
                .contains("\"message\":\"line one\\n\\\"line two\\\"\"")
                .contains("\"flags\":[\"a\",true]");
    }

    @Test
    void reopening_the_sink_appends_without_truncating_existing_events() throws Exception {
        Path file = temporaryDirectory.resolve("events.jsonl");
        try (JsonLinesFlowerTraceSink sink = new JsonLinesFlowerTraceSink(file)) {
            sink.publish(event(1));
        }
        try (JsonLinesFlowerTraceSink sink = new JsonLinesFlowerTraceSink(file)) {
            sink.publish(event(2));
        }

        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).hasSize(2);
    }

    @Test
    void serialization_and_size_failures_do_not_write_partial_lines() throws Exception {
        Path unsupportedFile = temporaryDirectory.resolve("unsupported.jsonl");
        try (JsonLinesFlowerTraceSink sink = new JsonLinesFlowerTraceSink(unsupportedFile)) {
            assertThatThrownBy(() -> sink.publish(event(1, "unsupported", new Object())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unsupported trace attribute type");
        }
        assertThat(Files.size(unsupportedFile)).isZero();

        Path oversizedFile = temporaryDirectory.resolve("oversized.jsonl");
        try (JsonLinesFlowerTraceSink sink = new JsonLinesFlowerTraceSink(oversizedFile, 128)) {
            assertThatThrownBy(() -> sink.publish(event(1, "large", repeat('x', 512))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxEventBytes");
        }
        assertThat(Files.size(oversizedFile)).isZero();
    }

    @Test
    void cyclic_attribute_values_are_rejected() throws Exception {
        Path file = temporaryDirectory.resolve("cyclic.jsonl");
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);

        try (JsonLinesFlowerTraceSink sink = new JsonLinesFlowerTraceSink(file)) {
            assertThatThrownBy(() -> sink.publish(event(1, "cyclic", cyclic)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cyclic");
        }
        assertThat(Files.size(file)).isZero();
    }

    private static String repeat(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    private static FlowerTraceEvent event(long sequence, Object... attributePairs) {
        FlowerTraceEvent.Builder builder = FlowerTraceEvent.builder(FlowerTraceEventType.FLOW_STARTED)
                .eventId("run-1:event:" + sequence)
                .traceId("trace-1")
                .flowRunId("run-1")
                .stepRunId("step-run-1")
                .parentRunId("parent-run-1")
                .flowId(FlowId.of("test", "1"))
                .workerName("test-worker")
                .sequence(sequence)
                .occurredAt(Instant.ofEpochMilli(sequence));
        for (int index = 0; index < attributePairs.length; index += 2) {
            builder.attribute((String) attributePairs[index], attributePairs[index + 1]);
        }
        return builder.build();
    }
}
