package io.github.parkkevinsb.flower.observability.tracing;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryFlowerListenerTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    private final OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    private final OpenTelemetryFlowerListener listener =
            new OpenTelemetryFlowerListener(openTelemetry);

    @AfterEach
    void shutdown() {
        exporter.reset();
        tracerProvider.shutdown();
    }

    @Test
    void recordsFlowAndStepSpansWithParenting() {
        FlowSnapshot submit = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);
        FlowSnapshot enter = snapshot("quay-work", "WO-1", FlowState.RUNNING, "execute-sts", 10);
        FlowSnapshot exit = snapshot("quay-work", "WO-1", FlowState.RUNNING, "execute-sts", 20);
        FlowSnapshot finish = snapshot("quay-work", "WO-1", FlowState.FINISHED, null, 0);

        listener.onFlowSubmitted(submit);
        listener.onStepEntered(enter, "execute-sts");
        listener.onStepExited(exit, "execute-sts");
        listener.onFlowFinished(finish);

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);
        SpanData flow = spanNamed(spans, FlowerTraceNames.FLOW_SPAN);
        SpanData step = spanNamed(spans, FlowerTraceNames.STEP_SPAN);

        assertThat(step.getTraceId()).isEqualTo(flow.getTraceId());
        assertThat(step.getParentSpanId()).isEqualTo(flow.getSpanId());
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_FLOW_TYPE)).isEqualTo("quay-work");
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_FLOW_KEY)).isEqualTo("WO-1");
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_OUTCOME))
                .isEqualTo(FlowerTraceNames.OUTCOME_FINISHED);
        assertThat(flow.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(stringAttr(step, FlowerTraceNames.ATTR_STEP_ID)).isEqualTo("execute-sts");
        assertThat(longAttr(step, FlowerTraceNames.ATTR_STEP_NO)).isEqualTo(20L);
    }

    @Test
    void failedFlowMarksSpanAsErrorAndRecordsException() {
        RuntimeException cause = new RuntimeException("boom");
        FlowSnapshot submit = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);
        FlowSnapshot fail = new FlowSnapshot(
                FlowId.of("quay-work", "WO-1"),
                FlowState.FAILED,
                null,
                0,
                cause);

        listener.onFlowSubmitted(submit);
        listener.onFlowFailed(fail, cause);

        SpanData flow = spanNamed(exporter.getFinishedSpanItems(), FlowerTraceNames.FLOW_SPAN);
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_OUTCOME))
                .isEqualTo(FlowerTraceNames.OUTCOME_FAILED);
        assertThat(flow.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(flow.getEvents()).extracting(event -> event.getName())
                .contains("exception");
    }

    @Test
    void terminalEventClosesDanglingStepSpan() {
        FlowSnapshot submit = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);
        FlowSnapshot enter = snapshot("quay-work", "WO-1", FlowState.RUNNING, "execute-sts", 10);
        FlowSnapshot cancel = snapshot("quay-work", "WO-1", FlowState.CANCELLED, null, 0);

        listener.onFlowSubmitted(submit);
        listener.onStepEntered(enter, "execute-sts");
        listener.onFlowCancelled(cancel);

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);
        SpanData step = spanNamed(spans, FlowerTraceNames.STEP_SPAN);
        SpanData flow = spanNamed(spans, FlowerTraceNames.FLOW_SPAN);

        assertThat(stringAttr(step, FlowerTraceNames.ATTR_OUTCOME))
                .isEqualTo(FlowerTraceNames.OUTCOME_CANCELLED);
        assertThat(step.getEvents()).extracting(event -> event.getName())
                .contains(FlowerTraceNames.EVENT_STEP_CLOSED_BY_FLOW_TERMINAL);
        assertThat(stringAttr(flow, FlowerTraceNames.ATTR_OUTCOME))
                .isEqualTo(FlowerTraceNames.OUTCOME_CANCELLED);
    }

    @Test
    void terminalWithoutSubmittedFlowDoesNotCreateSpan() {
        FlowSnapshot finish = snapshot("quay-work", "WO-1", FlowState.FINISHED, null, 0);

        listener.onFlowFinished(finish);

        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    private static FlowSnapshot snapshot(String flowType, String flowKey, FlowState state,
                                         String currentStepId, int currentStepNo) {
        return new FlowSnapshot(FlowId.of(flowType, flowKey), state, currentStepId, currentStepNo, null);
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

    private static Long longAttr(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.longKey(key));
    }
}
