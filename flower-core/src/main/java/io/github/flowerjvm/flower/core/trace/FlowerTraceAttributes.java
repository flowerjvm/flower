package io.github.flowerjvm.flower.core.trace;

/** Attribute names used by {@link FlowerTraceEvent}. */
public final class FlowerTraceAttributes {

    public static final String FLOW_STATE = "flower.flow.state";
    public static final String FLOW_PERSISTENCE = "flower.flow.persistence";
    public static final String FLOW_DEFINITION_VERSION = "flower.flow.definition.version";
    public static final String FLOW_RECOVERED = "flower.flow.recovered";
    public static final String STEP_ID = "flower.step.id";
    public static final String STEP_NO = "flower.step.no";
    public static final String STEP_RECOVERED = "flower.step.recovered";
    public static final String STEP_TRANSITION_ORIGIN = "flower.step.transition.origin";
    public static final String STEP_OUTCOME = "flower.step.outcome";
    public static final String STEP_TARGET_ID = "flower.step.target.id";
    public static final String ERROR_TYPE = "error.type";
    public static final String ERROR_MESSAGE = "error.message";
    public static final String TENANT_ID = "flower.context.tenant.id";
    public static final String USER_ID = "flower.context.user.id";
    public static final String SESSION_ID = "flower.context.session.id";
    public static final String CORRELATION_ID = "flower.context.correlation.id";

    private FlowerTraceAttributes() {
    }
}
