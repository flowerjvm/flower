package io.github.flowerjvm.flower.core.trace;

/** Attribute names used by {@link FlowerTraceEvent}. */
public final class FlowerTraceAttributes {

    public static final String FLOW_STATE = "flower.flow.state";
    public static final String FLOW_PERSISTENCE = "flower.flow.persistence";
    public static final String FLOW_DEFINITION_VERSION = "flower.flow.definition.version";
    public static final String FLOW_RECOVERED = "flower.flow.recovered";
    public static final String FLOW_RUNTIME_ID = "flower.flow.runtime.id";
    public static final String FLOW_RECOVERY_WORKER = "flower.flow.recovery.worker";
    public static final String FLOW_RECOVERY_CHECKPOINT_AT_MILLIS =
            "flower.flow.recovery.checkpoint.at.millis";
    public static final String STEP_ID = "flower.step.id";
    public static final String STEP_NO = "flower.step.no";
    public static final String STEP_RECOVERED = "flower.step.recovered";
    public static final String STEP_CALLBACK = "flower.step.callback";
    public static final String STEP_TRANSITION_ORIGIN = "flower.step.transition.origin";
    public static final String STEP_OUTCOME = "flower.step.outcome";
    public static final String STEP_TARGET_ID = "flower.step.target.id";
    public static final String WAIT_GENERATION = "flower.wait.generation";
    public static final String WAIT_CONDITIONS = "flower.wait.conditions";
    public static final String WAIT_CONDITION_TYPE = "type";
    public static final String WAIT_EVENT_TYPE = "eventType";
    public static final String WAIT_EVENT_PREDICATE = "predicate";
    public static final String WAIT_SIGNAL_NAME = "signalName";
    public static final String WAIT_SIGNAL_KEY = "signalKey";
    public static final String WAIT_DEADLINE_AT_MILLIS = "deadlineAtMillis";
    public static final String RESUME_REASON = "flower.resume.reason";
    public static final String RESUME_EVENT_TYPE = "flower.resume.event.type";
    public static final String RESUME_SIGNAL_NAME = "flower.resume.signal.name";
    public static final String RESUME_SIGNAL_KEY = "flower.resume.signal.key";
    public static final String RESUME_DEADLINE_AT_MILLIS =
            "flower.resume.deadline.at.millis";
    public static final String CHECKPOINT_ACTION = "flower.checkpoint.action";
    public static final String CHECKPOINT_UPDATED_AT_MILLIS =
            "flower.checkpoint.updated.at.millis";
    public static final String CHECKPOINT_STEP_ENTERED = "flower.checkpoint.step.entered";
    public static final String CHECKPOINT_AWAIT_GENERATION =
            "flower.checkpoint.await.generation";
    public static final String ERROR_TYPE = "error.type";
    public static final String ERROR_MESSAGE = "error.message";
    public static final String TENANT_ID = "flower.context.tenant.id";
    public static final String USER_ID = "flower.context.user.id";
    public static final String SESSION_ID = "flower.context.session.id";
    public static final String CORRELATION_ID = "flower.context.correlation.id";

    private FlowerTraceAttributes() {
    }
}
