package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link FlowerListener} that publishes Flow / Step lifecycle spans to
 * OpenTelemetry.
 *
 * <p>The listener records orchestration lifecycle spans:
 * <ul>
 *   <li>{@code flower.flow}: accepted by Worker -&gt; finished / failed / cancelled</li>
 *   <li>{@code flower.step}: step entered -&gt; step exited</li>
 * </ul>
 *
 * <p>Step spans are children of the Flow span when the Flow submit event was
 * observed by this listener. If the listener is registered mid-flight and sees
 * only Step events, those Step spans are emitted as root spans.
 */
public final class OpenTelemetryFlowerListener implements FlowerListener {

    private final Tracer tracer;
    private final ConcurrentMap<FlowId, Span> flowSpans = new ConcurrentHashMap<>();
    private final ConcurrentMap<StepKey, Span> stepSpans = new ConcurrentHashMap<>();

    public OpenTelemetryFlowerListener(OpenTelemetry openTelemetry) {
        this(openTelemetry, FlowerTraceNames.INSTRUMENTATION_NAME);
    }

    public OpenTelemetryFlowerListener(OpenTelemetry openTelemetry, String instrumentationName) {
        if (openTelemetry == null) {
            throw new IllegalArgumentException("openTelemetry must not be null");
        }
        if (instrumentationName == null || instrumentationName.trim().isEmpty()) {
            throw new IllegalArgumentException("instrumentationName must not be blank");
        }
        this.tracer = openTelemetry.getTracer(instrumentationName);
    }

    public OpenTelemetryFlowerListener(Tracer tracer) {
        if (tracer == null) {
            throw new IllegalArgumentException("tracer must not be null");
        }
        this.tracer = tracer;
    }

    @Override
    public void onFlowSubmitted(FlowSnapshot flow) {
        FlowId id = flow.flowId();
        Span span = tracer.spanBuilder(FlowerTraceNames.FLOW_SPAN)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(FlowerTraceNames.ATTR_FLOW_TYPE, id.flowType())
                .setAttribute(FlowerTraceNames.ATTR_FLOW_KEY, id.flowKey())
                .setAttribute(FlowerTraceNames.ATTR_FLOW_STATE, flow.state().name())
                .startSpan();

        Span previous = flowSpans.put(id, span);
        if (previous != null) {
            previous.addEvent(FlowerTraceNames.EVENT_FLOW_REPLACED);
            previous.setAttribute(FlowerTraceNames.ATTR_OUTCOME, FlowerTraceNames.OUTCOME_REPLACED);
            previous.end();
        }
    }

    @Override
    public void onStepEntered(FlowSnapshot flow, String stepId) {
        FlowId id = flow.flowId();
        Span parent = flowSpans.get(id);
        SpanBuilder builder = tracer.spanBuilder(FlowerTraceNames.STEP_SPAN)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(FlowerTraceNames.ATTR_FLOW_TYPE, id.flowType())
                .setAttribute(FlowerTraceNames.ATTR_FLOW_KEY, id.flowKey())
                .setAttribute(FlowerTraceNames.ATTR_FLOW_STATE, flow.state().name())
                .setAttribute(FlowerTraceNames.ATTR_STEP_ID, safe(stepId))
                .setAttribute(FlowerTraceNames.ATTR_STEP_NO, flow.currentStepNo());

        if (parent != null) {
            builder.setParent(Context.current().with(parent));
        } else {
            builder.setNoParent();
        }

        Span span = builder.startSpan();
        StepKey key = new StepKey(id, stepId);
        Span previous = stepSpans.put(key, span);
        if (previous != null) {
            previous.addEvent(FlowerTraceNames.EVENT_STEP_REPLACED);
            previous.setAttribute(FlowerTraceNames.ATTR_OUTCOME, FlowerTraceNames.OUTCOME_REPLACED);
            previous.end();
        }
    }

    @Override
    public void onStepExited(FlowSnapshot flow, String stepId) {
        StepKey key = new StepKey(flow.flowId(), stepId);
        Span span = stepSpans.remove(key);
        if (span == null) return;

        span.setAttribute(FlowerTraceNames.ATTR_FLOW_STATE, flow.state().name());
        span.setAttribute(FlowerTraceNames.ATTR_STEP_NO, flow.currentStepNo());
        span.end();
    }

    @Override
    public void onFlowFinished(FlowSnapshot flow) {
        recordFlowTerminal(flow, FlowerTraceNames.OUTCOME_FINISHED, null);
    }

    @Override
    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
        recordFlowTerminal(flow, FlowerTraceNames.OUTCOME_FAILED, cause);
    }

    @Override
    public void onFlowCancelled(FlowSnapshot flow) {
        recordFlowTerminal(flow, FlowerTraceNames.OUTCOME_CANCELLED, null);
    }

    private void recordFlowTerminal(FlowSnapshot flow, String outcome, Throwable cause) {
        FlowId id = flow.flowId();
        closeDanglingStepSpans(id, outcome, cause);

        Span span = flowSpans.remove(id);
        if (span == null) return;

        span.setAttribute(FlowerTraceNames.ATTR_FLOW_STATE, flow.state().name());
        span.setAttribute(FlowerTraceNames.ATTR_OUTCOME, outcome);
        if (cause != null) {
            span.recordException(cause);
            span.setStatus(StatusCode.ERROR, errorDescription(cause));
        } else if (FlowerTraceNames.OUTCOME_FINISHED.equals(outcome)) {
            span.setStatus(StatusCode.OK);
        }
        span.end();
    }

    private void closeDanglingStepSpans(FlowId id, String outcome, Throwable cause) {
        for (Map.Entry<StepKey, Span> entry : stepSpans.entrySet()) {
            StepKey key = entry.getKey();
            Span span = entry.getValue();
            if (!id.equals(key.flowId)) {
                continue;
            }
            if (stepSpans.remove(key, span)) {
                span.addEvent(FlowerTraceNames.EVENT_STEP_CLOSED_BY_FLOW_TERMINAL);
                span.setAttribute(FlowerTraceNames.ATTR_OUTCOME, outcome);
                if (cause != null) {
                    span.recordException(cause);
                    span.setStatus(StatusCode.ERROR, errorDescription(cause));
                }
                span.end();
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String errorDescription(Throwable cause) {
        String message = cause.getMessage();
        return message == null ? cause.getClass().getName() : message;
    }

    private static final class StepKey {
        private final FlowId flowId;
        private final String stepId;

        StepKey(FlowId flowId, String stepId) {
            this.flowId = flowId;
            this.stepId = stepId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StepKey)) return false;
            StepKey that = (StepKey) o;
            if (!flowId.equals(that.flowId)) return false;
            return stepId == null ? that.stepId == null : stepId.equals(that.stepId);
        }

        @Override
        public int hashCode() {
            int h = flowId.hashCode();
            h = 31 * h + (stepId == null ? 0 : stepId.hashCode());
            return h;
        }
    }
}
