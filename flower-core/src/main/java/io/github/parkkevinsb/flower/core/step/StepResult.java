package io.github.parkkevinsb.flower.core.step;

/**
 * Outcome of a single {@link Step#onTick(StepContext)} invocation.
 *
 * <p>Java 8 compatible: {@code sealed} is intentionally avoided. The discriminator
 * is the {@link Type} enum and the supporting fields are populated only for the
 * relevant type.
 *
 * <pre>
 * STAY     stay on current Step, run onTick again next tick
 * ADVANCE  finish current Step, move to next Step in flow order
 * REPEAT   reset current Step and run it again from the start
 * GOTO     finish current Step, jump to a Step by string id
 * DONE     finish the entire Flow successfully
 * FAIL     terminate the Flow with a Throwable
 * </pre>
 */
public final class StepResult {

    public enum Type {
        STAY,
        ADVANCE,
        REPEAT,
        GOTO,
        DONE,
        FAIL
    }

    private static final StepResult STAY = new StepResult(Type.STAY, null, null, null);
    private static final StepResult ADVANCE = new StepResult(Type.ADVANCE, null, null, null);
    private static final StepResult REPEAT = new StepResult(Type.REPEAT, null, null, null);
    private static final StepResult DONE = new StepResult(Type.DONE, null, null, null);

    private final Type type;
    private final String targetStepId;
    private final GoToMode goToMode;
    private final Throwable cause;

    private StepResult(Type type, String targetStepId, GoToMode goToMode, Throwable cause) {
        this.type = type;
        this.targetStepId = targetStepId;
        this.goToMode = goToMode;
        this.cause = cause;
    }

    public static StepResult stay() {
        return STAY;
    }

    public static StepResult advance() {
        return ADVANCE;
    }

    public static StepResult repeat() {
        return REPEAT;
    }

    public static StepResult goTo(String stepId) {
        if (stepId == null || stepId.isEmpty()) {
            throw new IllegalArgumentException("goTo target stepId must not be null or empty");
        }
        return new StepResult(Type.GOTO, stepId, GoToMode.COMPLETE_CURRENT, null);
    }

    public static StepResult done() {
        return DONE;
    }

    public static StepResult fail(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("fail cause must not be null");
        }
        return new StepResult(Type.FAIL, null, null, cause);
    }

    public Type type() {
        return type;
    }

    public String targetStepId() {
        return targetStepId;
    }

    public GoToMode goToMode() {
        return goToMode;
    }

    public Throwable cause() {
        return cause;
    }

    @Override
    public String toString() {
        switch (type) {
            case GOTO:
                return "StepResult{GOTO " + targetStepId + " " + goToMode + "}";
            case FAIL:
                return "StepResult{FAIL " + cause + "}";
            default:
                return "StepResult{" + type + "}";
        }
    }
}
