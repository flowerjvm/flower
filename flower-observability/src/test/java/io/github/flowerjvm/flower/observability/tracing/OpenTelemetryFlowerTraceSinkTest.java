package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.trace.FlowerTraceAttributes;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryFlowerTraceSinkTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    private final OpenTelemetryFlowerTraceSink sink = new OpenTelemetryFlowerTraceSink(
            OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build());

    @AfterEach
    void shutdown() {
        exporter.reset();
        tracerProvider.shutdown();
    }

    @Test
    void maps_standard_wait_resume_trace_to_parented_spans() {
        sink.publish(event(FlowerTraceEventType.FLOW_STARTED, 1L, null, attributes()));
        sink.publish(event(FlowerTraceEventType.STEP_STARTED, 2L, "runtime-1:step:1", attributes(
                FlowerTraceAttributes.STEP_ID, "call-model")));
        sink.publish(event(FlowerTraceEventType.FLOW_WAITING, 3L, "runtime-1:step:1", attributes(
                FlowerTraceAttributes.RESUME_REASON, "SIGNAL")));
        sink.publish(event(FlowerTraceEventType.FLOW_RESUMED, 4L, "runtime-1:step:1", attributes(
                FlowerTraceAttributes.RESUME_REASON, "SIGNAL")));
        sink.publish(event(FlowerTraceEventType.STEP_COMPLETED, 5L, "runtime-1:step:1", attributes(
                FlowerTraceAttributes.STEP_OUTCOME, "FINISH")));
        sink.publish(event(FlowerTraceEventType.FLOW_COMPLETED, 6L, null, attributes()));

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);
        SpanData flow = spanNamed(spans, FlowerTraceNames.FLOW_SPAN);
        SpanData step = spanNamed(spans, FlowerTraceNames.STEP_SPAN);

        assertThat(step.getTraceId()).isEqualTo(flow.getTraceId());
        assertThat(step.getParentSpanId()).isEqualTo(flow.getSpanId());
        assertThat(step.getEvents()).extracting(event -> event.getName())
                .contains(
                        FlowerTraceEventType.FLOW_WAITING.name(),
                        FlowerTraceEventType.FLOW_RESUMED.name());
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_FLOW_RUN_ID)).isEqualTo("run-1");
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_FLOW_RUNTIME_ID)).isEqualTo("runtime-1");
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_OUTCOME))
                .isEqualTo(FlowerTraceNames.OUTCOME_FINISHED);
        assertThat(stringAttr(step, FlowerTraceNames.ATTR_OUTCOME)).isEqualTo("finish");
        assertThat(flow.getStartEpochNanos()).isEqualTo(1_001_000_000L);
        assertThat(flow.getEndEpochNanos()).isEqualTo(1_006_000_000L);
        assertThat(step.getStartEpochNanos()).isEqualTo(1_002_000_000L);
        assertThat(step.getEndEpochNanos()).isEqualTo(1_005_000_000L);
    }

    private static FlowerTraceEvent event(
            FlowerTraceEventType type,
            long sequence,
            String stepRunId,
            Map<String, Object> attributes) {
        Map<String, Object> all = new LinkedHashMap<>(attributes);
        all.put(FlowerTraceAttributes.FLOW_RUNTIME_ID, "runtime-1");
        return FlowerTraceEvent.builder(type)
                .eventId("runtime-1:event:" + sequence)
                .traceId("trace-1")
                .flowRunId("run-1")
                .stepRunId(stepRunId)
                .parentRunId(stepRunId == null ? null : "run-1")
                .flowId(FlowId.of("agent", "flow-1"))
                .workerName("event-worker")
                .sequence(sequence)
                .occurredAt(Instant.ofEpochMilli(1_000L + sequence))
                .attributes(all)
                .build();
    }

    private static Map<String, Object> attributes(Object... entries) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            attributes.put((String) entries[i], entries[i + 1]);
        }
        return attributes;
    }

    private static SpanData spanNamed(List<SpanData> spans, String name) {
        for (SpanData span : spans) {
            if (name.equals(span.getName())) {
                return span;
            }
        }
        throw new AssertionError("span not found: " + name + " in " + spans);
    }

    private static String stringAttr(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key));
    }
}
