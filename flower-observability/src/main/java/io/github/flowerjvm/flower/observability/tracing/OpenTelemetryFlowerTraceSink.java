package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceAttributes;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
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
 * Maps the standard {@link FlowerTraceEvent} stream to OpenTelemetry spans.
 *
 * <p>Each Flower runtime segment becomes one {@code flower.flow} span and each
 * Step run becomes a child {@code flower.step} span. Waiting, resume,
 * checkpoint, and recovery facts are attached as span events. A recovered
 * process starts a new OpenTelemetry span because Flower does not persist an
 * SDK-specific SpanContext; the stable Flower trace, Flow run, and runtime ids
 * remain available as attributes for correlation in the backend.
 */
public final class OpenTelemetryFlowerTraceSink implements FlowerTraceSink {

    private final Tracer tracer;
    private final ConcurrentMap<String, Span> flowSpans = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StepSpan> stepSpans = new ConcurrentHashMap<>();

    public OpenTelemetryFlowerTraceSink(OpenTelemetry openTelemetry) {
        this(openTelemetry, FlowerTraceNames.INSTRUMENTATION_NAME);
    }

    public OpenTelemetryFlowerTraceSink(
            OpenTelemetry openTelemetry,
            String instrumentationName) {
        if (openTelemetry == null) {
            throw new IllegalArgumentException("openTelemetry must not be null");
        }
        if (instrumentationName == null || instrumentationName.trim().isEmpty()) {
            throw new IllegalArgumentException("instrumentationName must not be blank");
        }
        this.tracer = openTelemetry.getTracer(instrumentationName);
    }

    public OpenTelemetryFlowerTraceSink(Tracer tracer) {
        if (tracer == null) {
            throw new IllegalArgumentException("tracer must not be null");
        }
        this.tracer = tracer;
    }

    @Override
    public void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        switch (event.type()) {
            case FLOW_STARTED:
            case FLOW_RECOVERED:
                startFlow(event);
                return;
            case STEP_STARTED:
                startStep(event);
                return;
            case STEP_COMPLETED:
            case STEP_FAILED:
            case STEP_CANCELLED:
                closeStep(event);
                return;
            case FLOW_COMPLETED:
            case FLOW_FAILED:
            case FLOW_CANCELLED:
            case FLOW_SUSPENDED:
                closeFlow(event);
                return;
            default:
                addRuntimeEvent(event);
        }
    }

    private void startFlow(FlowerTraceEvent event) {
        String runtimeId = runtimeId(event);
        Span span = tracer.spanBuilder(FlowerTraceNames.FLOW_SPAN)
                .setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(attributes(event))
                .setStartTimestamp(event.occurredAt())
                .startSpan();
        span.addEvent(event.type().name(), attributes(event), event.occurredAt());
        Span previous = flowSpans.put(runtimeId, span);
        if (previous != null) {
            previous.addEvent(FlowerTraceNames.EVENT_FLOW_REPLACED);
            previous.setAttribute(FlowerTraceNames.ATTR_OUTCOME, FlowerTraceNames.OUTCOME_REPLACED);
            previous.end();
        }
    }

    private void startStep(FlowerTraceEvent event) {
        String stepRunId = event.stepRunId();
        if (stepRunId == null) {
            addRuntimeEvent(event);
            return;
        }
        String runtimeId = runtimeId(event);
        Span parent = flowSpans.get(runtimeId);
        SpanBuilder builder = tracer.spanBuilder(FlowerTraceNames.STEP_SPAN)
                .setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(attributes(event))
                .setStartTimestamp(event.occurredAt());
        if (parent != null) {
            builder.setParent(Context.current().with(parent));
        } else {
            builder.setNoParent();
        }
        Span span = builder.startSpan();
        span.addEvent(event.type().name(), attributes(event), event.occurredAt());
        StepSpan previous = stepSpans.put(stepRunId, new StepSpan(runtimeId, span));
        if (previous != null) {
            previous.span.addEvent(FlowerTraceNames.EVENT_STEP_REPLACED);
            previous.span.setAttribute(
                    FlowerTraceNames.ATTR_OUTCOME,
                    FlowerTraceNames.OUTCOME_REPLACED);
            previous.span.end();
        }
    }

    private void closeStep(FlowerTraceEvent event) {
        String stepRunId = event.stepRunId();
        StepSpan step = stepRunId == null ? null : stepSpans.remove(stepRunId);
        if (step == null) {
            addRuntimeEvent(event);
            return;
        }
        step.span.addEvent(event.type().name(), attributes(event), event.occurredAt());
        String outcome = stringAttribute(event, FlowerTraceAttributes.STEP_OUTCOME);
        if (outcome != null) {
            step.span.setAttribute(FlowerTraceNames.ATTR_OUTCOME, outcome.toLowerCase());
        }
        if (event.type() == FlowerTraceEventType.STEP_FAILED) {
            step.span.setStatus(StatusCode.ERROR, errorDescription(event));
        } else if (event.type() == FlowerTraceEventType.STEP_COMPLETED) {
            step.span.setStatus(StatusCode.OK);
        }
        step.span.end(event.occurredAt());
    }

    private void closeFlow(FlowerTraceEvent event) {
        String runtimeId = runtimeId(event);
        closeDanglingSteps(runtimeId, event);
        Span span = flowSpans.remove(runtimeId);
        if (span == null) {
            return;
        }
        span.addEvent(event.type().name(), attributes(event), event.occurredAt());
        switch (event.type()) {
            case FLOW_COMPLETED:
                span.setAttribute(FlowerTraceNames.ATTR_OUTCOME, FlowerTraceNames.OUTCOME_FINISHED);
                span.setStatus(StatusCode.OK);
                break;
            case FLOW_FAILED:
                span.setAttribute(FlowerTraceNames.ATTR_OUTCOME, FlowerTraceNames.OUTCOME_FAILED);
                span.setStatus(StatusCode.ERROR, errorDescription(event));
                break;
            case FLOW_CANCELLED:
                span.setAttribute(FlowerTraceNames.ATTR_OUTCOME, FlowerTraceNames.OUTCOME_CANCELLED);
                break;
            case FLOW_SUSPENDED:
                span.setAttribute(FlowerTraceNames.ATTR_OUTCOME, FlowerTraceNames.OUTCOME_SUSPENDED);
                break;
            default:
                // handled by caller switch
        }
        span.end(event.occurredAt());
    }

    private void addRuntimeEvent(FlowerTraceEvent event) {
        Span span = null;
        if (event.stepRunId() != null) {
            StepSpan step = stepSpans.get(event.stepRunId());
            if (step != null) {
                span = step.span;
            }
        }
        if (span == null) {
            span = flowSpans.get(runtimeId(event));
        }
        if (span != null) {
            span.addEvent(event.type().name(), attributes(event), event.occurredAt());
            if (event.type() == FlowerTraceEventType.CHECKPOINT_FAILED) {
                span.setStatus(StatusCode.ERROR, errorDescription(event));
            }
        }
    }

    private void closeDanglingSteps(String runtimeId, FlowerTraceEvent terminal) {
        for (Map.Entry<String, StepSpan> entry : stepSpans.entrySet()) {
            StepSpan step = entry.getValue();
            if (!runtimeId.equals(step.runtimeId)) {
                continue;
            }
            if (stepSpans.remove(entry.getKey(), step)) {
                step.span.addEvent(
                        FlowerTraceNames.EVENT_STEP_CLOSED_BY_FLOW_TERMINAL,
                        terminal.occurredAt());
                step.span.addEvent(
                        terminal.type().name(),
                        attributes(terminal),
                        terminal.occurredAt());
                if (terminal.type() == FlowerTraceEventType.FLOW_FAILED) {
                    step.span.setStatus(StatusCode.ERROR, errorDescription(terminal));
                }
                step.span.end(terminal.occurredAt());
            }
        }
    }

    private static Attributes attributes(FlowerTraceEvent event) {
        AttributesBuilder builder = Attributes.builder()
                .put(FlowerTraceNames.ATTR_TRACE_ID, event.traceId())
                .put(FlowerTraceNames.ATTR_FLOW_RUN_ID, event.flowRunId())
                .put(FlowerTraceNames.ATTR_FLOW_RUNTIME_ID, runtimeId(event))
                .put(FlowerTraceNames.ATTR_EVENT_TYPE, event.type().name())
                .put(FlowerTraceNames.ATTR_FLOW_TYPE, event.flowId().flowType())
                .put(FlowerTraceNames.ATTR_FLOW_KEY, event.flowId().flowKey());
        if (event.stepRunId() != null) {
            builder.put(FlowerTraceNames.ATTR_STEP_RUN_ID, event.stepRunId());
        }
        for (Map.Entry<String, Object> entry : event.attributes().entrySet()) {
            put(builder, entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static void put(AttributesBuilder builder, String key, Object value) {
        if (value instanceof Boolean) {
            builder.put(key, (Boolean) value);
        } else if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            builder.put(key, ((Number) value).longValue());
        } else if (value instanceof Float || value instanceof Double) {
            builder.put(key, ((Number) value).doubleValue());
        } else if (value != null) {
            builder.put(key, String.valueOf(value));
        }
    }

    private static String runtimeId(FlowerTraceEvent event) {
        Object runtimeId = event.attributes().get(FlowerTraceAttributes.FLOW_RUNTIME_ID);
        return runtimeId == null ? event.flowRunId() : String.valueOf(runtimeId);
    }

    private static String stringAttribute(FlowerTraceEvent event, String name) {
        Object value = event.attributes().get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static String errorDescription(FlowerTraceEvent event) {
        String message = stringAttribute(event, FlowerTraceAttributes.ERROR_MESSAGE);
        if (message != null) {
            return message;
        }
        String type = stringAttribute(event, FlowerTraceAttributes.ERROR_TYPE);
        return type == null ? event.type().name() : type;
    }

    private static final class StepSpan {
        private final String runtimeId;
        private final Span span;

        private StepSpan(String runtimeId, Span span) {
            this.runtimeId = runtimeId;
            this.span = span;
        }
    }
}
