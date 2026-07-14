package io.github.flowerjvm.flower.observability.metrics;

/**
 * Metric and tag names emitted by {@link MicrometerFlowerListener}. Exposed so
 * dashboards / alert rules can reference the same constants.
 */
public final class FlowerMetricNames {

    public static final String FLOW_SUBMITTED = "flower.flow.submitted";
    public static final String FLOW_FINISHED = "flower.flow.finished";
    public static final String FLOW_FAILED = "flower.flow.failed";
    public static final String FLOW_CANCELLED = "flower.flow.cancelled";
    public static final String FLOW_DURATION = "flower.flow.duration";

    public static final String STEP_ENTERED = "flower.step.entered";
    public static final String STEP_DURATION = "flower.step.duration";

    public static final String TAG_FLOW_TYPE = "flowType";
    public static final String TAG_STEP_ID = "stepId";
    public static final String TAG_OUTCOME = "outcome";

    public static final String OUTCOME_FINISHED = "finished";
    public static final String OUTCOME_FAILED = "failed";
    public static final String OUTCOME_CANCELLED = "cancelled";

    private FlowerMetricNames() {
    }
}
