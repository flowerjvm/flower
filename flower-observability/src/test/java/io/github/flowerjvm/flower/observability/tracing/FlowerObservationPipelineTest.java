package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.trace.FlowerTraceAttributes;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerObservationPipelineTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void core_trace_adapter_preserves_correlation_and_native_event_type() {
        InMemoryFlowerObservationSink destination = new InMemoryFlowerObservationSink();
        FlowerTraceObservationSink adapter = new FlowerTraceObservationSink(destination);
        FlowerTraceEvent source = FlowerTraceEvent.builder(FlowerTraceEventType.STEP_STARTED)
                .eventId("runtime-1:event:2")
                .traceId("trace-1")
                .flowRunId("flow-run-1")
                .stepRunId("step-run-1")
                .parentRunId("parent-run-1")
                .flowId(FlowId.of("order", "order-1"))
                .workerName("main")
                .sequence(2)
                .occurredAt(Instant.ofEpochMilli(2))
                .attribute(FlowerTraceAttributes.STEP_ID, "authorize")
                .build();

        adapter.publish(source);

        FlowerObservationEvent event = destination.snapshot().get(0);
        assertThat(event.source()).isEqualTo("flower-core");
        assertThat(event.eventType()).isEqualTo("STEP_STARTED");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.runId()).isEqualTo("flow-run-1");
        assertThat(event.parentRunId()).isEqualTo("parent-run-1");
        assertThat(event.operationId()).isEqualTo("step-run-1");
        assertThat(event.operationName()).isEqualTo("authorize");
        assertThat(event.attributes())
                .containsEntry(FlowerTraceObservationSink.FLOW_TYPE, "order")
                .containsEntry(FlowerTraceObservationSink.FLOW_KEY, "order-1")
                .containsEntry(FlowerTraceObservationSink.WORKER_NAME, "main");
    }

    @Test
    void sanitization_is_fail_closed_and_really_removes_attributes() {
        InMemoryFlowerObservationSink destination = new InMemoryFlowerObservationSink();
        SanitizingFlowerObservationSink sink = new SanitizingFlowerObservationSink(
                destination,
                FlowerObservationSanitizers.removeAttributes("secret"));

        sink.publish(event("trace-1", 1, attributes(
                "secret", "value",
                "safe", "visible")));

        assertThat(destination.snapshot().get(0).attributes())
                .doesNotContainKey("secret")
                .containsEntry("safe", "visible");

        SanitizingFlowerObservationSink broken = new SanitizingFlowerObservationSink(
                destination,
                ignored -> {
                    throw new IllegalStateException("broken");
                });
        broken.publish(event("trace-2", 2,
                Collections.singletonMap("secret", "must-not-pass")));
        assertThat(destination.size()).isEqualTo(1);
        assertThat(broken.failureCount()).isEqualTo(1);
    }

    @Test
    void probability_sampler_keeps_each_trace_as_a_whole() {
        FlowerObservationSampler sampler = FlowerObservationSamplers.probability(0.5d);
        long selected = 0;
        for (int index = 0; index < 1_000; index++) {
            String traceId = "observation-" + index;
            boolean first = sampler.sample(event(traceId, 1, Collections.emptyMap()));
            boolean second = sampler.sample(event(traceId, 2, Collections.emptyMap()));
            assertThat(second).isEqualTo(first);
            if (first) {
                selected++;
            }
        }
        assertThat(selected).isBetween(300L, 700L);
    }

    @Test
    void recommended_pipeline_writes_sanitized_common_events_as_json_lines()
            throws Exception {
        Path file = temporaryDirectory.resolve("observations/events.jsonl");
        JsonLinesFlowerObservationSink json = new JsonLinesFlowerObservationSink(file);
        AsyncFlowerObservationSink async = new AsyncFlowerObservationSink(
                json,
                8,
                "observation-pipeline-test");
        FlowerObservationSink sampled = new SamplingFlowerObservationSink(
                async,
                FlowerObservationSamplers.always());
        FlowerObservationSink ingress = new SanitizingFlowerObservationSink(
                sampled,
                FlowerObservationSanitizers.removeAttributes("secret"));
        try {
            ingress.publish(event("trace-1", 1, attributes(
                    "secret", "must-not-be-stored",
                    "status", "SUCCEEDED")));
        } finally {
            async.close();
            json.close();
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
                .contains("\"source\":\"flower-agent\"")
                .contains("\"eventType\":\"MODEL_CALL_COMPLETED\"")
                .contains("\"traceId\":\"trace-1\"")
                .contains("\"status\":\"SUCCEEDED\"")
                .doesNotContain("secret")
                .doesNotContain("must-not-be-stored");
        assertThat(async.publishedCount()).isEqualTo(1);
    }

    @Test
    void async_sink_close_with_zero_timeout_does_not_wait_for_consumer() throws Exception {
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        AsyncFlowerObservationSink sink = new AsyncFlowerObservationSink(event -> {
            consumerEntered.countDown();
            awaitUninterruptibly(releaseConsumer);
        }, 1, "observation-zero-close-test");
        Thread closer = new Thread(() -> {
            sink.close(0);
            closeReturned.countDown();
        }, "observation-zero-close-caller");

        boolean returnedWithoutWaiting;
        try {
            sink.publish(event("trace-1", 1, Collections.emptyMap()));
            assertThat(consumerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            closer.start();
            returnedWithoutWaiting = closeReturned.await(1, TimeUnit.SECONDS);
        } finally {
            releaseConsumer.countDown();
            closer.join(2_000L);
            sink.close();
        }

        assertThat(returnedWithoutWaiting).isTrue();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static FlowerObservationEvent event(
            String traceId,
            long sequence,
            Map<String, ?> attributes) {
        return FlowerObservationEvent.builder("flower-agent", "MODEL_CALL_COMPLETED")
                .eventId("agent-run-1:event:" + sequence)
                .traceId(traceId)
                .runId("agent-run-1")
                .operationId("model-call-1")
                .operationName("model-1")
                .sequence(sequence)
                .occurredAt(Instant.ofEpochMilli(sequence))
                .attributes(attributes)
                .build();
    }

    private static Map<String, Object> attributes(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put((String) keyValues[index], keyValues[index + 1]);
        }
        return values;
    }
}
