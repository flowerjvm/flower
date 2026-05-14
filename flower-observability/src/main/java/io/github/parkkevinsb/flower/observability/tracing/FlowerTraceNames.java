package io.github.parkkevinsb.flower.observability.tracing;

/**
 * OpenTelemetry span names, attribute names, and event names emitted by
 * {@link OpenTelemetryFlowerListener}.
 */
public final class FlowerTraceNames {

    public static final String INSTRUMENTATION_NAME = "io.github.parkkevinsb.flower";

    public static final String FLOW_SPAN = "flower.flow";
    public static final String STEP_SPAN = "flower.step";

    public static final String ATTR_FLOW_TYPE = "flower.flow.type";
    public static final String ATTR_FLOW_KEY = "flower.flow.key";
    public static final String ATTR_FLOW_STATE = "flower.flow.state";
    public static final String ATTR_OUTCOME = "flower.outcome";
    public static final String ATTR_STEP_ID = "flower.step.id";
    public static final String ATTR_STEP_NO = "flower.step.no";

    public static final String OUTCOME_FINISHED = "finished";
    public static final String OUTCOME_FAILED = "failed";
    public static final String OUTCOME_CANCELLED = "cancelled";
    public static final String OUTCOME_REPLACED = "replaced";

    public static final String EVENT_STEP_CLOSED_BY_FLOW_TERMINAL =
            "flower.step.closed_by_flow_terminal";
    public static final String EVENT_FLOW_REPLACED = "flower.flow.replaced";
    public static final String EVENT_STEP_REPLACED = "flower.step.replaced";

    private FlowerTraceNames() {
    }
}
