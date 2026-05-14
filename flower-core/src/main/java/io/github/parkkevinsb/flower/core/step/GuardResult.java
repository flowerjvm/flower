package io.github.parkkevinsb.flower.core.step;

/**
 * Result of a {@link Guard} check.
 */
public final class GuardResult {

    public enum Type {
        PASS,
        HOLD,
        GOTO,
        FAIL
    }

    private static final GuardResult PASS = new GuardResult(Type.PASS, null, null);
    private static final GuardResult HOLD = new GuardResult(Type.HOLD, null, null);

    private final Type type;
    private final String targetStepId;
    private final Throwable cause;

    private GuardResult(Type type, String targetStepId, Throwable cause) {
        this.type = type;
        this.targetStepId = targetStepId;
        this.cause = cause;
    }

    public static GuardResult pass() {
        return PASS;
    }

    public static GuardResult hold() {
        return HOLD;
    }

    public static GuardResult goTo(String stepId) {
        if (stepId == null || stepId.isEmpty()) {
            throw new IllegalArgumentException("guard goTo target stepId must not be null or empty");
        }
        return new GuardResult(Type.GOTO, stepId, null);
    }

    public static GuardResult fail(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("guard fail cause must not be null");
        }
        return new GuardResult(Type.FAIL, null, cause);
    }

    public Type type() {
        return type;
    }

    public String targetStepId() {
        return targetStepId;
    }

    public Throwable cause() {
        return cause;
    }

    @Override
    public String toString() {
        switch (type) {
            case GOTO:
                return "GuardResult{GOTO " + targetStepId + "}";
            case FAIL:
                return "GuardResult{FAIL " + cause + "}";
            default:
                return "GuardResult{" + type + "}";
        }
    }
}
