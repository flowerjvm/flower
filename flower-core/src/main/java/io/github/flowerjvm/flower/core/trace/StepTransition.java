package io.github.flowerjvm.flower.core.trace;

/**
 * The effective transition selected while a Flow was positioned at one Step.
 *
 * <p>This is separate from {@code StepResult}: guards, lifecycle failures, and
 * external cancellation can also move or terminate a Flow.
 */
public final class StepTransition {

    public enum Origin {
        STEP_RESULT,
        GUARD,
        LIFECYCLE,
        EXTERNAL
    }

    public enum Outcome {
        DONE,
        REPEAT,
        GOTO,
        FINISH,
        FAILED,
        CANCELLED
    }

    private final String stepId;
    private final int stepNo;
    private final Origin origin;
    private final Outcome outcome;
    private final String targetStepId;
    private final Throwable cause;

    private StepTransition(
            String stepId,
            int stepNo,
            Origin origin,
            Outcome outcome,
            String targetStepId,
            Throwable cause) {
        if (stepId == null || stepId.trim().isEmpty()) {
            throw new IllegalArgumentException("stepId must not be blank");
        }
        if (stepNo < 0) {
            throw new IllegalArgumentException("stepNo must not be negative: " + stepNo);
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if ((outcome == Outcome.GOTO || outcome == Outcome.REPEAT)
                && (targetStepId == null || targetStepId.trim().isEmpty())) {
            throw new IllegalArgumentException("targetStepId is required for " + outcome);
        }
        if (outcome == Outcome.FAILED && cause == null) {
            throw new IllegalArgumentException("cause is required for FAILED");
        }
        this.stepId = stepId;
        this.stepNo = stepNo;
        this.origin = origin;
        this.outcome = outcome;
        this.targetStepId = targetStepId;
        this.cause = cause;
    }

    public static StepTransition of(
            String stepId,
            int stepNo,
            Origin origin,
            Outcome outcome,
            String targetStepId) {
        return new StepTransition(stepId, stepNo, origin, outcome, targetStepId, null);
    }

    public static StepTransition failed(
            String stepId,
            int stepNo,
            Origin origin,
            String targetStepId,
            Throwable cause) {
        return new StepTransition(stepId, stepNo, origin, Outcome.FAILED, targetStepId, cause);
    }

    public String stepId() {
        return stepId;
    }

    public int stepNo() {
        return stepNo;
    }

    public Origin origin() {
        return origin;
    }

    public Outcome outcome() {
        return outcome;
    }

    public String targetStepId() {
        return targetStepId;
    }

    public Throwable cause() {
        return cause;
    }

    @Override
    public String toString() {
        return "StepTransition{" + stepId + "/no=" + stepNo
                + " " + origin + ":" + outcome
                + (targetStepId != null ? " -> " + targetStepId : "")
                + (cause != null ? " cause=" + cause : "")
                + "}";
    }
}
