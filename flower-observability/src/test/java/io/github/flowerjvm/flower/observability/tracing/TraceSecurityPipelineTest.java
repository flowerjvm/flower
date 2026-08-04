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
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TraceSecurityPipelineTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void sanitizers_remove_and_redact_exact_attributes_without_changing_identity() {
        InMemoryFlowerTraceSink destination = new InMemoryFlowerTraceSink();
        TraceSanitizer sanitizer = TraceSanitizers.compose(
                TraceSanitizers.removeAttributes("api.key"),
                TraceSanitizers.redactAttributes("[REDACTED]", "user.email"));
        SanitizingFlowerTraceSink sink = new SanitizingFlowerTraceSink(destination, sanitizer);
        FlowerTraceEvent original = event("trace-1", 1,
                "api.key", "secret-key",
                "user.email", "person@example.com",
                "safe", "visible");

        sink.publish(original);

        FlowerTraceEvent sanitized = destination.snapshot().get(0);
        assertThat(sanitized.eventId()).isEqualTo(original.eventId());
        assertThat(sanitized.traceId()).isEqualTo(original.traceId());
        assertThat(sanitized.attributes())
                .doesNotContainKey("api.key")
                .containsEntry("user.email", "[REDACTED]")
                .containsEntry("safe", "visible");
        assertThat(sink.publishedCount()).isEqualTo(1);
        assertThat(sink.droppedCount()).isZero();
    }

    @Test
    void sanitization_failures_and_null_results_drop_events_fail_closed() {
        InMemoryFlowerTraceSink destination = new InMemoryFlowerTraceSink();
        SanitizingFlowerTraceSink throwing = new SanitizingFlowerTraceSink(
                destination,
                event -> {
                    throw new IllegalStateException("cannot sanitize");
                });
        SanitizingFlowerTraceSink returningNull = new SanitizingFlowerTraceSink(
                destination,
                event -> null);

        throwing.publish(event("trace-1", 1));
        returningNull.publish(event("trace-2", 2));

        assertThat(destination.snapshot()).isEmpty();
        assertThat(throwing.droppedCount()).isEqualTo(1);
        assertThat(throwing.failureCount()).isEqualTo(1);
        assertThat(returningNull.droppedCount()).isEqualTo(1);
        assertThat(returningNull.failureCount()).isEqualTo(1);
    }

    @Test
    void probability_sampler_makes_one_stable_decision_per_trace() {
        TraceSampler sampler = TraceSamplers.probability(0.5d);

        for (int index = 0; index < 100; index++) {
            String traceId = "trace-" + index;
            assertThat(sampler.sample(event(traceId, 1)))
                    .isEqualTo(sampler.sample(event(traceId, 2)));
        }

        long selected = 0;
        for (int index = 0; index < 1_000; index++) {
            if (sampler.sample(event("sample-" + index, 1))) {
                selected++;
            }
        }
        assertThat(selected).isBetween(300L, 700L);
    }

    @Test
    void sampling_failure_drops_the_event() {
        InMemoryFlowerTraceSink destination = new InMemoryFlowerTraceSink();
        SamplingFlowerTraceSink sink = new SamplingFlowerTraceSink(destination, event -> {
            throw new IllegalStateException("sampling unavailable");
        });

        sink.publish(event("trace-1", 1));

        assertThat(destination.snapshot()).isEmpty();
        assertThat(sink.droppedCount()).isEqualTo(1);
        assertThat(sink.failureCount()).isEqualTo(1);
    }

    @Test
    void content_policy_inlines_small_content_and_externalizes_large_content()
            throws Exception {
        InMemoryFlowerTraceSink destination = new InMemoryFlowerTraceSink();
        Path artifactRoot = temporaryDirectory.resolve("artifacts");
        FileTraceArtifactStore artifactStore = new FileTraceArtifactStore(artifactRoot);
        ContentCaptureFlowerTraceSink sink = new ContentCaptureFlowerTraceSink(
                destination,
                TraceContentPolicies.inlineOrArtifact(8),
                artifactStore);
        TraceContent secretSafeDescription = TraceContent.text("short");
        TraceContent large = TraceContent.text("application/json", "{\"answer\":\"a long result\"}");
        FlowerTraceEvent source = event("trace-1", 1,
                "model.summary", secretSafeDescription,
                "tool.result", large);

        sink.publish(source);

        FlowerTraceEvent captured = destination.snapshot().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> inline =
                (Map<String, Object>) captured.attributes().get("model.summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> artifact =
                (Map<String, Object>) captured.attributes().get("tool.result");
        assertThat(inline)
                .containsEntry("capture", "INLINE")
                .containsEntry("text", "short");
        assertThat(artifact)
                .containsEntry("capture", "ARTIFACT")
                .containsKeys("artifactId", "location", "sha256");
        Path artifactFile = artifactRoot.resolve((String) artifact.get("location"));
        assertThat(new String(Files.readAllBytes(artifactFile), StandardCharsets.UTF_8))
                .isEqualTo(large.text());
        assertThat(sink.inlineContentCount()).isEqualTo(1);
        assertThat(sink.artifactContentCount()).isEqualTo(1);
        assertThat(secretSafeDescription.toString()).doesNotContain("short");

        sink.publish(event("trace-2", 2, "tool.result", large));
        try (Stream<Path> files = Files.walk(artifactRoot)) {
            assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
    }

    @Test
    void content_capture_failure_does_not_forward_raw_content() {
        InMemoryFlowerTraceSink destination = new InMemoryFlowerTraceSink();
        ContentCaptureFlowerTraceSink sink = new ContentCaptureFlowerTraceSink(
                destination,
                TraceContentPolicies.artifactAll());

        sink.publish(event("trace-1", 1, "model.prompt", TraceContent.text("private")));

        assertThat(destination.snapshot()).isEmpty();
        assertThat(sink.failedCount()).isEqualTo(1);
    }

    @Test
    void recommended_pipeline_sanitizes_before_async_json_storage() throws Exception {
        Path traceFile = temporaryDirectory.resolve("pipeline/events.jsonl");
        JsonLinesFlowerTraceSink json = new JsonLinesFlowerTraceSink(traceFile);
        ContentCaptureFlowerTraceSink content = new ContentCaptureFlowerTraceSink(
                json,
                TraceContentPolicies.inlineUpTo(1_024));
        AsyncFlowerTraceSink async = new AsyncFlowerTraceSink(
                content,
                8,
                "trace-security-pipeline-test");
        FlowerTraceSink sampled = new SamplingFlowerTraceSink(async, TraceSamplers.always());
        FlowerTraceSink ingress = new SanitizingFlowerTraceSink(
                sampled,
                TraceSanitizers.removeAttributes("api.key"));
        try {
            ingress.publish(event("trace-1", 1,
                    "api.key", TraceContent.text("must-not-leave-worker"),
                    "model.summary", TraceContent.text("safe summary")));
        } finally {
            async.close();
            json.close();
        }

        String stored = new String(Files.readAllBytes(traceFile), StandardCharsets.UTF_8);
        assertThat(stored)
                .doesNotContain("must-not-leave-worker")
                .doesNotContain("api.key")
                .contains("safe summary")
                .contains("\"capture\":\"INLINE\"");
        assertThat(async.publishedCount()).isEqualTo(1);
    }

    private static FlowerTraceEvent event(
            String traceId,
            long sequence,
            Object... attributePairs) {
        FlowerTraceEvent.Builder builder = FlowerTraceEvent.builder(FlowerTraceEventType.FLOW_STARTED)
                .eventId(traceId + ":event:" + sequence)
                .traceId(traceId)
                .flowRunId("run-1")
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
